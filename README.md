# datanucleus-core

DataNucleus core persistence support - the basis for anything in DataNucleus.

This is built using Maven, by executing `mvn clean install` which installs the built jar in your local Maven repository.

## KeyFacts

__License__ : Apache 2 licensed  
__Issue Tracker__ : http://www.datanucleus.org/servlet/jira/browse/NUCCORE  
__RoadMap__ : http://issues.datanucleus.org/browse/NUCCORE?report=com.atlassian.jira.plugin.system.project:roadmap-panel  
__Javadocs__ : [5.0](http://www.datanucleus.org/javadocs/core/5.0/), [4.1](http://www.datanucleus.org/javadocs/core/4.1/), [4.0](http://www.datanucleus.org/javadocs/core/4.0/), [3.2](http://www.datanucleus.org/javadocs/core/3.2/), [3.1](http://www.datanucleus.org/javadocs/core/3.1/), [3.0](http://www.datanucleus.org/javadocs/core/3.0/), [2.2](http://www.datanucleus.org/javadocs/core/2.2/), [2.1](http://www.datanucleus.org/javadocs/core/2.1/), [2.0](http://www.datanucleus.org/javadocs/core/2.0/), [1.1](http://www.datanucleus.org/javadocs/core/1.1/), [1.0](http://www.datanucleus.org/javadocs/core/1.0/)  
__Download(Releases)__ : [Maven Central](http://central.maven.org/maven2/org/datanucleus/datanucleus-core)  
__Download(Nightly)__ : [Nightly Builds](http://www.datanucleus.org/downloads/maven2-nightly/org/datanucleus/datanucleus-core)  
__Dependencies__ : See file [pom.xml](pom.xml)  

----  

## Persistence Process
The primary classes involved in the persistence process are
*ExecutionContext* - maps across to a PM/EM, and handles the transaction (ExecutionContextImpl)  
*ObjectProvider* - manages access to a persistent object (StateManagerImpl)  
*StoreManager* - manages access to the datastore (see the datastore plugins, e.g RDBMSStoreManager)  
*MetaDataManager* - manages the metadata for the class(es), so how it is persisted  

### Persistence : Retrieve of Objects

    MyClass myObj = (MyClass)pm.getObjectById(id);
    myObj.getSomeSet().add(newVal);

* calls backing store wrapper (see _org.datanucleus.store.types.backed.XXX_ or _org.datanucleus.store.types.simple.XXX_)
* if optimistic txns then queues up til flush/commit
* otherwise will call backing store for the wrapper (RDBMS) which updates the DB, or will mark the field as dirty (non-RDBMS) and the field is sent to the datastore at the next convenient place.


    Query q = pm.newQuery("SELECT FROM " + MyClass.class.getName());
    List<MyClass> results = (List<MyClass>)q.execute();

* Makes use of QueryManager to create an internal Query object (wrapped by a JDO/JPA Query object). This may be something like org.datanucleus.store.rdbms.query.JDOQLQuery specific to 
the datastore.
* The query is compiled generically. This involves converting each component of the query (filter, ordering, grouping, result etc) into Node trees, and then converting that into Expression trees. 
This is then stored in a QueryCompilation, and can be cached.
* The query is then converted into a datastore-specific compilation. In the case of RDBMS this will be an RDBMSCompilation, and will be an SQL string (and associated parameter/result lookups).
* The query is executed in the datastore and/or in-memory. The in-memory evaluator is in datanucleus-core under org.datanucleus.query.evaluator.memory. 
The execution process will return a QueryResult (which is a List).
* Operations on the QueryResult such as "iterator()" will result in lazy loading of results from the underlying ResultSet (in the case of RDBMS)



<a name="pessimistic"/>
### Persistence : Pessimistic Transactions

All persist, remove, field update calls go to the datastore straight away. 
Flush() doesn't have the same significance here as it does for optimistic, except in that it will queue "update" requests until there are more than say 3 objects waiting.
This means that multiple setters can be called on a single object and we get one UPDATE statement.


####persist
Calls ExecutionContext.persistObject which calls EC.persistObjectWork.  
Creates an ObjectProvider (StateManagerImpl - OP). Adds the object to EC.dirtyOPs.  
Calls OP.makePersistent which calls OP.internalMakePersistent which will pass the persist through to the datastore plugin.  
Calls PersistenceHandler.insertObject, which will do any necessary cascade persist (coming back through EC.persistObjectInternal, EC.indirectDirtyOPs).  


####remove
Calls ExecutionContext.deleteObject, which calls ExecutionContext.deleteObjectWork.  
This will add the object to EC.dirtyOPs.  
Calls OP.deletePersistent.  
Calls OP.internalDeletePersistent which will pass the delete through to the datastore plugin.  
Calls PersistenceHandler.deleteObject, which will do any necessary cascade delete (coming back through EC.deleteObjectInternal, EC.indirectDirtyOPs).  


####update field
Calls OP.setXXXField which calls OP.updateField and, in turn, EC.makeDirty.  
The update is then queued internally until EC.flushInternal is triggered (e.g 3 changes waiting).  


####Collection.add
Calls SCO wrapper.add which will add the element locally.  
If a backing store is present (RDBMS) then passes it through to the backingStore.add().  


####Collection.remove/clear
Calls SCO wrapper.remove/clear which will add the element locally.  
If a backing store is present (RDBMS) then passes it through to the backingStore.remove()/clear().  
If no backing store is present and cascade delete is true then does the cascade delete, via EC.deleteObjectInternal.  


<a name="optimistic"/>
### Persistence : Optimistic Transactions

All persist, remove, field update calls are queued.
Flush() processes all remove/add/updates that have been queued.
Call ExecutionContext.getOperationQueue() to see the operations that are queued up waiting to flush.


####persist
Calls ExecutionContext.persistObject which calls EC.persistObjectWork.  
Creates an ObjectProvider (StateManagerImpl - OP). Adds the object to EC.dirtyOPs.  
Calls OP.makePersistent. Uses PersistFieldManager to process all reachable objects.  


####remove
Calls ExecutionContext.deleteObject, which calls ExecutionContext.deleteObjectWork.  
Creates an ObjectProvider as required. Adds the object to EC.dirtyOPs.  
Calls OP.deletePersistent. Uses DeleteFieldManager to process all reachable objects.


####update field
Calls OP.setXXXField which calls OP.updateField and, in turn, EC.makeDirty.  
The update is then queued internally until EC.flushInternal is triggered.  


####Collection.add
Calls SCO wrapper.add which will add the element locally.  
Adds a queued operation to the queue for addition of this element.  


####Collection.remove/clear
Calls SCO wrapper.remove/clear which will add the element locally.  
Adds a queued operation to the queue for removal of this element.  

----  


### Query Process

DataNucleus provides a _generic_ query processing engine. It provides for compilation of __string-based query languages__. 
Additionally it allows _in-memory evaluation_ of these queries. This is very useful when providing support for new datastores which either
don't have a native query language and so the only alternative is for DataNucleus to evaluate the queries, or where it will take some time 
to map the compiled query to the equivalent query in the native language of the datastore.

#### Query : Input Processing

When a user invokes a query, using the JDO/JPA APIs, they are providing either

* A single-string query made up of keywords and clauses
* A query object that has the clauses specified directly

The first step is to convert these two forms into the constituent clauses. It is assumed that a string-based query is of the form

	SELECT {resultClause} FROM {fromClause} WHERE {filterClause}
	GROUP BY {groupingClause} HAVING {havingClause}
	ORDER BY {orderClause}]]></source>

The two primary supported query languages have helper classes to provide this migration from the _single-string query form_ into the individual clauses. 
These can be found in _org.datanucleus.query.JDOQLSingleStringParser_
[![Javadoc](../../images/javadoc.gif)](http://www.datanucleus.org/javadocs/core/latest/org/datanucleus/query/JDOQLSingleStringParser.html)
and _org.datanucleus.query.JPQLSingleStringParser_
[![Javadoc](../../images/javadoc.gif)](http://www.datanucleus.org/javadocs/core/latest/org/datanucleus/query/JPQLSingleStringParser.html).

#### Query : Compilation

So we have a series of clauses and we want to compile them. So what does this mean? Well, in simple terms, we are going to convert the individual clauses 
from above into expression tree(s) so that they can be evaluated. The end result of a compilation is a _org.datanucleus.query.compiler.QueryCompilation_
[![Javadoc](../../images/javadoc.gif)](http://www.datanucleus.org/javadocs/core/latest/org/datanucleus/query/compiler/QueryCompilation.html).

So if you think about a typical query you may have

	SELECT field1, field2 FROM MyClass

This has 2 result expressions - field1, and field2 (where they are each a "PrimaryExpression" meaning a representation of a field).
The query compilation of a particular clauses has 2 stages

1. Compilation into a Node tree, with operations between the nodes
2. Compilation of the Node tree into an Expression tree of supported expressions

and compilation is performed by a JavaQueryCompiler, so look at _org.datanucleus.query.compiler.JDOQLCompiler_
[![Javadoc](../../images/javadoc.gif)](http://www.datanucleus.org/javadocs/core/latest/org/datanucleus/query/compiler/JDOQLCompiler.html)
and _org.datanucleus.query.compiler.JPQLCompiler_
[![Javadoc](../../images/javadoc.gif)](http://www.datanucleus.org/javadocs/core/latest/org/datanucleus/query/compiler/JPQLCompiler.html).
These each have a Parser that performs the extraction of the different components of the clauses and generation of the Node tree. 
Once a Node tree is generated it can then be converted into the compiled Expression tree; this is handled inside the JavaQueryCompiler.

The other part of a query compilation is the _org.datanucleus.query.symbol.SymbolTable_
[![Javadoc](../../images/javadoc.gif)](http://www.datanucleus.org/javadocs/core/latest/org/datanucleus/query/symbol/SymbolTable.html)
which is a lookup table (map) of identifiers and their value. So, for example, an input parameter will have a name, so has an entry in 
the table, and its value is stored there. This is then used during evaluation.

#### Query : Evaluation In-datastore

Intuitively it is more efficient to evaluate a query within the datastore since it means that fewer actual result objects need 
instantiating in order to determine the result objects. To evaluate a compiled query in the datastore there needs to be a compiler 
for taking the generic expression compilation and converting it into a native query. Additionally it should be noted that you aren't 
forced to evaluate the whole of the query in the datastore, maybe just the filter clause. This would be done where the datastore 
native language maybe only provides a limited amount of query capabilities. For example with db4o we evaluated the _filter_ and 
_ordering_ in the datastore, using their SODA query language. The remaining clauses can be evaluated on the resultant objects 
_in-memory_ (see below). Obviously for a datastore like RDBMS it should be possible to evaluate the whole query in-datastore.

#### Query : Evaluation In-memory

Evaluation of queries in-memory assumes that we have a series of "candidate" objects. These are either user-input to the query itself, 
or retrieved from the datastore. We then use the in-memory evaluator _org.datanucleus.query.evaluator.memory.InMemoryExpressionEvaluator_
[![Javadoc](../../images/javadoc.gif)](http://www.datanucleus.org/javadocs/core/latest/org/datanucleus/query/evaluator/memory/InMemoryExpressionEvaluator.html).
This takes in each candidate object one-by-one and evaluates whichever of the query clauses are desired to be evaluated. 
For example we could just evaluate the filter clause. Evaluation makes use of the values of the fields of the candidate objects 
(and related objects) and uses the SymbolTable for values of parameters etc. Where a candidate fails a particular clause 
in the filter then it is excluded from the results.

#### Query : Results

There are two primary ways to return results to the user.

* Instantiate all into memory and return a (java.util.)List. This is the simplest, but obviously can impact on memory footprint.
* Return a wrapper to a List, and intercept calls so that you can load objects as they are accessed. This is more complex, 
but has the advantage of not imposing a large footprint on the application.

To make use of the second route, consider extending the class _org.datanucleus.store.query.AbstractQueryResult_ and implement the key methods.
Also, for the iterator, you can extend _org.datanucleus.store.query.AbstractQueryResultIterator_.

