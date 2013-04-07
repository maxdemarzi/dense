Dense Nodes
===========

Dealing with Dense Nodes in any database can be difficult.  
This is an attempt to mitigate the problem by fanning out the relationships.

The solution must allow the nodes and relationships to be easily referenced via Cypher.

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

From Webadmin:

    POST /example/service/node/1/dense_relationships {"other" : "http://localhost:7474/db/data/node/2", "type" : "LOVES", "direction" : "INCOMING", "data" : { "foo" : "bar"}}

    START me=node(1)
    MATCH me<-[:LOVES]-()<-[:DENSE_LOVES*0..5]-loved
    WHERE NOT HAS(loved.meta)
    RETURN loved

    START me=node(2)
    MATCH me-[:DENSE_LOVES*0..5]->()-[:LOVES]-loved
    WHERE NOT HAS(loved.meta)
    RETURN loved



Performance Test
----------------

Test read and write performance while writing to a "dense" node.
