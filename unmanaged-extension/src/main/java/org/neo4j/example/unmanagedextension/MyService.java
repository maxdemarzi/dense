package org.neo4j.example.unmanagedextension;

import org.codehaus.jackson.map.ObjectMapper;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.*;
import org.neo4j.server.rest.repr.BadInputException;
import org.neo4j.server.rest.repr.InputFormat;
import org.neo4j.server.rest.repr.OutputFormat;
import org.neo4j.server.rest.repr.formats.JsonFormat;

import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.util.*;

@Path("/service")
public class MyService {

    private final InputFormat input = new JsonFormat();
    private static final Integer MAXRELS = 100;
    private static final String PREFIX = "DENSE_";
    ObjectMapper objectMapper = new ObjectMapper();

    @GET
    @Path("/friends/{name}")
    public Response getFriends(@PathParam("name") String name, @Context GraphDatabaseService db) throws IOException {
        ExecutionEngine executionEngine = new ExecutionEngine(db);
        ExecutionResult result = executionEngine.execute("START person=node:people(name={n}) MATCH person-[:KNOWS]-other RETURN other.name",
                Collections.<String, Object>singletonMap("n", name));
        List<String> friends = new ArrayList<String>();
        for (Map<String, Object> item : result) {
            friends.add((String) item.get("other.name"));
        }
        return Response.ok().entity(objectMapper.writeValueAsString(friends)).build();
    }

    @POST
    @Path("/node/{nodeId}/dense_relationships")
    @Produces("application/json")
    public Response createDenseRelationship(String body, @PathParam("nodeId") long nodeId, @Context GraphDatabaseService db) throws Exception
    {
        final OutputFormat output = new OutputFormat( new JsonFormat(), new URI( "http://localhost/" ), null );
        Map<String, Object> result;

        final Map<String, Object> data;
        final long otherNodeId;
        final RelationshipType type;
        final Direction direction;
        final Map<String, Object> properties;

        Integer metaCount;
        Integer branchCount;
        long[] nextBranchArray;
        long nextBranchId;

        Node denseNode;
        Node sparseNode;
        Node metaNode;
        Node nextMetaNode;

        Relationship metaRelationship;
        Relationship createdRelationship = null;
        RelationshipType metaType;

        try
        {
            data = input.readMap(body);
            otherNodeId = extractNodeId((String) data.get("other"));
            type = extractRelationshipType((String) data.get("type"));
            metaType = extractRelationshipType(PREFIX + data.get("type"));
            direction = extractDirection((String) data.get("direction"));
            properties = (Map<String, Object>) data.get("data");
        }
        catch ( BadInputException e )
        {
            return output.badRequest( e );
        }
        catch ( ClassCastException e )
        {
            return output.badRequest( e );
        }

        Transaction tx = db.beginTx();
        try
        {
            denseNode = db.getNodeById(nodeId);
            sparseNode = db.getNodeById(otherNodeId);
            metaRelationship = denseNode.getSingleRelationship(type, direction);

            // If this is our first time, create a new relationship type meta node and connect via
            // a new metaRelationship, that points to this new node in the next property.
            if (metaRelationship == null) {
                metaNode = db.createNode();
                metaCount = 0;
                branchCount = 0;
                metaNode.setProperty("meta", true);
                metaNode.setProperty("meta_count", 0);
                metaNode.setProperty("branch_count", 0);
                metaRelationship = Relate(denseNode, metaNode, type, direction);
                metaRelationship.setProperty("next", metaNode.getId());
                nextBranchArray = new long[]{ metaNode.getId() };
                metaRelationship.setProperty("next_branch", nextBranchArray);

            } else {
              metaNode = db.getNodeById((Long)metaRelationship.getProperty("next"));
              metaCount = (Integer) metaNode.getProperty("meta_count");
              nextBranchArray = (long[]) metaRelationship.getProperty("next_branch");
              nextBranchId = nextBranchArray[0];
              branchCount = (Integer) db.getNodeById(nextBranchId).getProperty("branch_count");
            }

            tx.acquireWriteLock(metaRelationship);

            // If our count is less than the maximum fan out, attach to existing meta node.
            // Else create a new meta node, and attach to it.
            if(metaCount < MAXRELS) {
                tx.acquireWriteLock(metaNode);
                metaNode.setProperty("meta_count", ++metaCount);
            } else {
                if (branchCount < MAXRELS){
                    ++branchCount;
                    metaNode.setProperty("branch_count", branchCount);

                } else {
                    nextBranchArray = Arrays.copyOfRange(nextBranchArray, 1, nextBranchArray.length );
                    branchCount = 1;
                }
                nextBranchId = nextBranchArray[0];
                metaNode = db.getNodeById(nextBranchId);

                nextMetaNode = db.createNode();
                nextMetaNode.setProperty("meta", true);
                nextMetaNode.setProperty("meta_count", 1);
                nextMetaNode.setProperty("branch_count", 0);

                Relate(metaNode, nextMetaNode, metaType, direction);
                metaRelationship.setProperty("next", nextMetaNode.getId());

                metaNode.setProperty("branch_count", branchCount);
                nextBranchArray = Arrays.copyOfRange(nextBranchArray, 0, nextBranchArray.length + 1);
                nextBranchArray[nextBranchArray.length - 1] = nextMetaNode.getId();
                metaRelationship.setProperty("next_branch", nextBranchArray);
            }

            // Create the actual relationship and set the properties
            createdRelationship = Relate(metaNode, sparseNode, metaType, direction);
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                createdRelationship.setProperty(entry.getKey(), entry.getValue());
            }

            tx.success();
        }
        finally
        {
            tx.finish();
            result = UpdateResult(createdRelationship, direction, type.name(), otherNodeId, nodeId);
        }

        try
        {
            return Response.created(new URI("http://localhost/db/data/relationship/" + createdRelationship.getId())).
                    entity(objectMapper.writeValueAsString(result)).build();
        }
        catch ( IllegalArgumentException e)
        {
            return output.badRequest( e );
        }
    }


    private long extractNodeId( String uri ) throws BadInputException
    {
        try
        {
            return Long.parseLong( uri.substring( uri.lastIndexOf( "/" ) + 1 ) );
        }
        catch ( NumberFormatException ex )
        {
            throw new BadInputException( ex );
        }
        catch ( NullPointerException ex )
        {
            throw new BadInputException( ex );
        }
    }

    private Direction extractDirection( String direction ) throws BadInputException
    {
        switch (Direction.valueOf( direction.toUpperCase() )){
            case INCOMING: return Direction.INCOMING;
            case OUTGOING: return Direction.OUTGOING;
            default: throw new BadInputException("Bad Direction: Only INCOMING and OUTGOING are allowed.");
            }
    }

    private DynamicRelationshipType extractRelationshipType( String type ) throws BadInputException
    {
        try
        {
            return DynamicRelationshipType.withName( type );
        }
        catch ( IllegalArgumentException ex )
        {
            throw new BadInputException( ex );
        }
    }

    private Relationship Relate( Node nodeA, Node nodeB, RelationshipType type, Direction direction ) throws BadInputException
    {
        try
        {
            if (direction == Direction.OUTGOING)  {
                return nodeA.createRelationshipTo( nodeB, type );
            } else {
                return nodeB.createRelationshipTo( nodeA, type );
            }
        }
        catch ( Exception ex )
        {
            throw new BadInputException( ex );
        }
    }
    private Map<String, Object> UpdateResult( Relationship createdRelationship, Direction direction, String type, long otherNodeId, long nodeId ) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("type", type);
        result.put("self", "http://localhost:7474/db/data/relationship/" + createdRelationship.getId());
        result.put("property", "http://localhost:7474/db/data/relationship/" + +createdRelationship.getId() + "/properties/{key}");
        result.put("properties", "http://localhost:7474/db/data/relationship/" + +createdRelationship.getId() + "/properties");
        if(direction == Direction.INCOMING)
        {
            result.put("start", "http://localhost:7474/db/data/" + otherNodeId);
            result.put("end", "http://localhost:7474/db/data/" + nodeId);
        }
        if(direction == Direction.OUTGOING)
        {
            result.put("start", "http://localhost:7474/db/data/" + nodeId);
            result.put("end", "http://localhost:7474/db/data/" + otherNodeId);
        }
        return result;
    }
}
