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


    POST /example/service/node/1/dense_relationships {"other" : "http://localhost:7474/db/data/node/2", "type" : "LIKES", "direction" : "INCOMING", "data" : { "foo" : "bar"}}
    POST /example/service/node/1/dense_relationships {"other" : "http://localhost:7474/db/data/node/3", "type" : "LIKES", "direction" : "INCOMING", "data" : { "foo" : "bar"}}
    POST /example/service/node/1/dense_relationships {"other" : "http://localhost:7474/db/data/node/4", "type" : "LIKES", "direction" : "INCOMING", "data" : { "foo" : "bar"}}
    POST /example/service/node/1/dense_relationships {"other" : "http://localhost:7474/db/data/node/5", "type" : "LIKES", "direction" : "INCOMING", "data" : { "foo" : "bar"}}
    POST /example/service/node/1/dense_relationships {"other" : "http://localhost:7474/db/data/node/6", "type" : "LIKES", "direction" : "INCOMING", "data" : { "foo" : "bar"}}
    POST /example/service/node/1/dense_relationships {"other" : "http://localhost:7474/db/data/node/7", "type" : "LIKES", "direction" : "INCOMING", "data" : { "foo" : "bar"}}
    POST /example/service/node/1/dense_relationships {"other" : "http://localhost:7474/db/data/node/8", "type" : "LIKES", "direction" : "INCOMING", "data" : { "foo" : "bar"}}

	START me=node(1) 
	MATCH me<-[:LIKES]-()<-[:DENSE_LIKES*0..1]-liked 
	WHERE NOT HAS(liked.meta) 
	RETURN COUNT(liked)

    START me=node(2)
    MATCH me-[:DENSE_LIKES*0..5]->()-[:LIKES]-loved
    RETURN loved


Performance Test
----------------

Test read and write performance while writing to a "dense" node.

Open the project in IntelliJ.  In src/test/scala, right click on Engine and Run that.
When the prompt appears, first run the Create Nodes simulation, and once that is finished, run the Engine again and choose the Create Relationships simulation.

10M nodes are created, and these are randomly connected to node 1.

While the imports are running try the cypher queries above, or create a new relationship (with a different type) to node 1 and see how well that responds.


To Run
------

Clone it, and in the unmanaged-extension directory run:

    mvn clean package


You'll want to copy the jar to your neo4j/plugins directory:

    cp unmanaged-extension/target/dense-node-extension-1.0.jar neo4j/plugins/

And add the following to the neo4j/conf/neo4j-server.properties:

    org.neo4j.server.thirdparty_jaxrs_classes=org.neo4j.example.unmanagedextension=/example

Start the Neo4j server.

The project includes two Gatling tests which simulate the creation of 10M nodes and connecting them together while executing reads at the same time.

