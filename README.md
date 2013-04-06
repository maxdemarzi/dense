Dense Nodes
===========

Dealing with Dense Nodes in any database can be difficult.  
This is an attempt to mitigate the problem by fanning out the relationships.

Description
-----------

This project will consist of 2 parts:

* An unmanaged extension
* A Gatling performance test

Unmanaged Extension
-------------------

A REST end point for adding relationships to a node that will be dense.

Example request:

* POST http://localhost:7474/db/data/node/341/dense_relationships
* Accept: application/json
* Content-Type: application/json

<pre>
    {
      "other" : "http://localhost:7474/db/data/node/340",
      "type" : "LOVES",
      "direction" : "INCOMING",
      "data" : {
        "foo" : "bar"
      }
    }
</pre>	
	
Example response:

* 201: Created
* Content-Type: application/json
* Location: http://localhost:7474/db/data/relationship/207	

<pre>	
    {
      "extensions" : {
      },
      "start" : "http://localhost:7474/db/data/node/413",
      "property" : "http://localhost:7474/db/data/relationship/207/properties/{key}",
      "self" : "http://localhost:7474/db/data/relationship/207",
      "properties" : "http://localhost:7474/db/data/relationship/207/properties",
      "type" : "_LOVES",
      "end" : "http://localhost:7474/db/data/node/340",
      "data" : {
	    "_start": "http://localhost:7474/db/data/node/340",
        "foo" : "bar"
      }
    }	
</pre>


Performance Test
----------------

Test read and write performance while writing to a "dense" node.

