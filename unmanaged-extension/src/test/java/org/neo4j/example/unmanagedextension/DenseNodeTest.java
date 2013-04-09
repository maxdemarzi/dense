package org.neo4j.example.unmanagedextension;

import com.sun.jersey.api.client.ClientResponse;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.*;
import org.neo4j.server.rest.domain.JsonHelper;
import org.neo4j.test.ImpermanentGraphDatabase;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import org.neo4j.server.rest.repr.RelationshipRepresentationTest;

public class DenseNodeTest {

    private ImpermanentGraphDatabase db;
    private DenseNode service;
    private ObjectMapper objectMapper = new ObjectMapper();
    private static final RelationshipType KNOWS = DynamicRelationshipType.withName("KNOWS");

    @Before
    public void setUp() {
        db = new ImpermanentGraphDatabase();
        populateDb(db);
        service = new DenseNode();
    }

    private void populateDb(GraphDatabaseService db) {
        Transaction tx = db.beginTx();
        try
        {
            int i;
            for (i=1; i <= 1000; i++) {
                createPerson(db, String.valueOf(i));
            }
            tx.success();
        }
        finally
        {
            tx.finish();
        }
    }

    private Node createPerson(GraphDatabaseService db, String name) {
        Node node = db.createNode();
        node.setProperty("name", name);
        return node;
    }

    @After
    public void tearDown() throws Exception {
        db.shutdown();

    }

    @Test
    public void shouldCreateDenseRelationship() throws Exception {

        String jsonString;
        int i;
        for (i=1; i <=100; i++) {
            jsonString = "{\"other\" : \""
                    + "http://localhost:7474/"
                    + "node/"
                    + String.valueOf(i)
                    + "\", \"type\" : \"LIKES\", "
                    + "\"direction\" : \"INCOMING\", "
                    + "\"data\" : {\"foo\" : \"bar\"}}";

            Response response = service.createDenseRelationship(jsonString, 3L, db);
            System.out.println(String.valueOf(i));
            //System.out.println(response.getStatus());
            System.out.println(response.getEntity().toString());

            //System.out.println(JsonHelper.jsonToMap((String) response.getEntity()));

            //assertProperRelationshipRepresentation( JsonHelper.jsonToMap( (String) response.getEntity() ) );

          //  Map items = objectMapper.readValue((String) response.getEntity(), Map.class);
          //  System.out.println(items);
        }
        //ObjectWriter writer = objectMapper.writerWithDefaultPrettyPrinter();
        //Object myObject;
        //myObject = objectMapper.readValue( json, Object.class );
       // 42            return writer.writeValueAsString(myObject );
        //System.out.println(writer.writeValueAsString(objectMapper.readValue(response.getEntity().toString(), Object.class)));
    }


    public GraphDatabaseService graphdb() {
        return db;
    }
}
