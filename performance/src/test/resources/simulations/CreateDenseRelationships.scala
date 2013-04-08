package simulations

import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.http.Predef._
import akka.util.duration._
import bootstrap._
import scala.util.parsing.json.{JSONArray, JSONObject}


class CreateRelationships extends Simulation {
  val httpConf = httpConfig
    .baseURL("http://localhost:7474")
    .acceptHeader("application/json")
  // Uncomment to see Requests
  //  .requestInfoExtractor(request => {
  //    println(request.getStringData)
  //    Nil
  //  })
  // Uncomment to see Response
  //  .responseInfoExtractor(response => {
  //    println(response.getResponseBody)
  //    Nil
  //  })
    .disableResponseChunksDiscarding


  val rnd = new scala.util.Random
  val chooseRandomNodes = exec((session) => {
    session.setAttribute("id", rnd.nextInt(10000000))
  })

  val json = "{\"other\" : \"http://localhost:7474/db/data/node/%s\", \"type\" : \"LIKES\", \"direction\" : \"INCOMING\", \"data\" : { \"foo\" : \"bar\"}}".format("${id}")

  val scn = scenario("Create Relationships")
    .during(6000) {
    exec(chooseRandomNodes)
      .exec(
      http("create relationships")
        .post("/example/service/node/1/dense_relationships")
        .body(json)
        .asJSON
        .check(status.is(201)))
      .pause(0 milliseconds, 5 milliseconds)
  }

  // We are just getting the first layer to see if speed slows down
  val fetchSomeLikes = """START me=node(1) MATCH me<-[:LIKES]-()<-[:DENSE_LIKES*0..1]-liked WHERE NOT HAS(liked.meta) RETURN COUNT(liked)"""
  val likesPostBody = """{"query": "%s"}""".format(fetchSomeLikes)

  val scn2 = scenario("Get Relationships")
    .during(6000) {
      exec(
      http("get relationships")
        .post("/db/data/cypher")
        .header("X-Stream", "true")
        .body(likesPostBody)
        .asJSON
        .check(status.is(200)))
        .pause(1 seconds)
  }

  setUp(
    scn.users(100).ramp(10).protocolConfig(httpConf),
    scn2.users(1).protocolConfig(httpConf)
  )
}
