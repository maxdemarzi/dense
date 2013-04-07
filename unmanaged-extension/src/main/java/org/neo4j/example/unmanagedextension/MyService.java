package org.neo4j.example.unmanagedextension;

import org.apache.commons.lang.ArrayUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.*;
import org.neo4j.server.rest.domain.EndNodeNotFoundException;
import org.neo4j.server.rest.domain.StartNodeNotFoundException;
import org.neo4j.server.rest.repr.BadInputException;
import org.neo4j.server.rest.repr.InputFormat;
import org.neo4j.server.rest.repr.OutputFormat;
import org.neo4j.server.rest.repr.RelationshipRepresentation;
import org.neo4j.server.rest.repr.formats.JsonFormat;
import org.neo4j.server.rest.web.PropertyValueException;

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
    private static final Integer MAXRELS = 3;
    private static final String PREFIX = "DENSE_";

    @GET
    @Path("/helloworld")
    public String helloWorld() {
        return "Hello World!";
    }

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
        ObjectMapper objectMapper = new ObjectMapper();
        return Response.ok().entity(objectMapper.writeValueAsString(friends)).build();
    }

    @POST
    @Path("/node/{nodeId}/dense_relationships")
    @Produces("application/json")
    public Response createDenseRelationship(String body, @PathParam("nodeId") Long nodeId, @Context GraphDatabaseService db) throws Exception
    {
        final OutputFormat output = new OutputFormat( new JsonFormat(), new URI( "http://localhost/" ), null );
        Map<String, Object> results = new HashMap<String, Object>();
        ObjectMapper objectMapper = new ObjectMapper();

        final Map<String, Object> data;
        final long otherNodeId;
        RelationshipType type;
        final Direction direction;
        final Map<String, Object> properties;

        Integer metaCount;
        Integer branchCount;
        List<Long> nextMetaArray = new ArrayList<Long>();
        Long nextId;
        Node denseNode;
        Node sparseNode;
        Node metaNode;
        Node nextMetaNode;
        Relationship metaRelationship;
        Relationship createdRelationship = null;
        RelationshipType metaType;
        final RelationshipRepresentation result;

        try
        {
            data = input.readMap( body );
            otherNodeId = extractNodeId((String) data.get("other"));
            type = extractRelationshipType((String) data.get("type"));
            metaType = extractRelationshipType(PREFIX + (String) data.get("type"));
            direction = extractDirection((String) data.get("direction"));
            properties = (Map<String, Object>) data.get( "data" );
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
                metaNode.setProperty("meta", true);
                metaNode.setProperty("count", 0);
                metaNode.setProperty("branch_count", 0);
                metaCount = 0;
                branchCount = 0;
                metaRelationship = Relate(denseNode, metaNode, type, direction);
                metaRelationship.setProperty("next", metaNode.getId());
                nextMetaArray.add(metaNode.getId());
                metaRelationship.setProperty("next_branch", nextMetaArray.toArray(new Long[nextMetaArray.size()]));
                System.out.println("ONLY ONCE: created a new meta Node and Relationship" + metaNode.getId());
            } else {
              metaNode = db.getNodeById((Long) metaRelationship.getProperty("next"));
              metaCount = (Integer) metaNode.getProperty("count");
              branchCount = (Integer) metaNode.getProperty("branch_count");
                try {
                    long[] meh = (long[])metaRelationship.getProperty("next_branch");
                    for(long l : meh) nextMetaArray.add(l);
                } catch (ClassCastException ex){
                    nextMetaArray = (List< Long >)Arrays.asList((Long[])metaRelationship.getProperty("next_branch")) ;
                    System.out.println("wtf");
                }


              //long[] meh = (long[])metaRelationship.getProperty("next_branch");
              //for(long l : meh) nextMetaArray.add(l);
            }

            tx.acquireWriteLock(metaRelationship);

            // If our count is less than the maximum fan out, attach to existing meta node.
            // Else create a new meta node, and attach to it.
            if(metaCount < MAXRELS) {
                tx.acquireWriteLock(metaNode);
                metaNode.setProperty("count", ++metaCount);
                System.out.println("Count: " + metaCount + " Attached to EXISTING meta node: " + metaNode.getId());
            } else {
                if (branchCount < MAXRELS){
                    ++branchCount;
                    metaNode.setProperty("branch_count", branchCount);
                    nextId = nextMetaArray.get(0);
                    metaNode = db.getNodeById(nextId);
                } else {
                    nextMetaArray.remove(0);
                    nextId = nextMetaArray.get(0);
                    metaNode = db.getNodeById(nextId);
                    System.out.println("IMPORTANT! branchCount: " + branchCount + " Attached to EXISTING meta node: " + metaNode.getId());
                    branchCount = 1;
                }

                System.out.println(Arrays.toString(nextMetaArray.toArray(new Long[nextMetaArray.size()])));

                nextMetaNode = db.createNode();
                nextMetaNode.setProperty("meta", true);
                nextMetaNode.setProperty("count", 1);
                nextMetaNode.setProperty("branch_count", 0);

                Relate(metaNode, nextMetaNode, metaType, direction);
                metaNode.setProperty("branch_count", branchCount);
                //metaRelationship.setProperty("next", nextMetaNode.getId());
                metaRelationship.setProperty("next", nextId);
                nextMetaArray.add(nextMetaNode.getId());
                metaRelationship.setProperty("next_branch", nextMetaArray.toArray(new Long[nextMetaArray.size()]));
                System.out.println("MetaCount: " + metaCount + "BranchCount: " + branchCount + " Attached to NEW meta node: " + nextMetaNode.getId());
                //long[] meh = (long[])metaRelationship.getProperty("next_branch");
                //System.out.println(Arrays.toString(meh));

            }

            createdRelationship = Relate(metaNode, sparseNode, metaType, direction);

            results.put("type", type.name());
            results.put("self", "http://localhost:7474/db/data/relationship/" + createdRelationship.getId());
            results.put("property", "http://localhost:7474/db/data/relationship/" + + createdRelationship.getId() + "/properties/{key}");
            results.put("properties", "http://localhost:7474/db/data/relationship/" + + createdRelationship.getId() + "/properties");
            if(direction == Direction.INCOMING)
            {
                results.put("start", "http://localhost:7474/db/data/" + otherNodeId );
                results.put("end", "http://localhost:7474/db/data/" + nodeId );
            }
            if(direction == Direction.OUTGOING)
            {
                results.put("start", "http://localhost:7474/db/data/" + nodeId );
                results.put("end", "http://localhost:7474/db/data/" + otherNodeId );
            }
           // TO-DO: Add Properties for(createdRelationship.)


            result = new RelationshipRepresentation( createdRelationship );
            tx.success();
        }
        finally
        {
            tx.finish();
        }

        try
        {
            return Response.created(new URI("http://localhost/db/data/relationship/" + createdRelationship.getId())).
                    entity(objectMapper.writeValueAsString(results)).build();
        }
//        catch ( StartNodeNotFoundException e )
//        {
//            return output.notFound( e );
//        }
//        catch ( EndNodeNotFoundException e )
//        {
//            return output.badRequest( e );
//        }
        catch ( IllegalArgumentException e)
        {
            return output.badRequest( e );
        }
//        catch ( PropertyValueException e )
//        {
//            return output.badRequest( e );
//        }
//        catch ( BadInputException e )
//        {
//            return output.badRequest( e );
//        }

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

    private Direction extractDirection(String direction) throws BadInputException
    {
        switch (Direction.valueOf(direction.toUpperCase())){
            case INCOMING: return Direction.INCOMING;
            case OUTGOING: return Direction.OUTGOING;
            default: throw new BadInputException("Bad Direction: Only INCOMING and OUTGOING are allowed.");
            }

    }

    private DynamicRelationshipType extractRelationshipType(String type) throws BadInputException
    {
        try
        {
            return DynamicRelationshipType.withName(type);
        }
        catch ( IllegalArgumentException ex)
        {
            throw new BadInputException( ex );
        }
    }

    private Relationship Relate(Node nodeA, Node nodeB, RelationshipType type, Direction direction) throws BadInputException
    {
        try
        {
            if (direction == Direction.OUTGOING)  {
                return nodeA.createRelationshipTo(nodeB, type);
            } else {
                return nodeB.createRelationshipTo(nodeA, type);
            }
        }
        catch ( Exception ex )
        {
            throw new BadInputException( ex );
        }
    }
}
