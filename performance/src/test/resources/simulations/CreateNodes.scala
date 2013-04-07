package simulations

import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.http.Predef._
import akka.util.duration._
import bootstrap._

class CreateNodes extends Simulation {

  val httpConf = httpConfig
    .baseURL("http://localhost:7474")
    .acceptHeader("application/json")

  val createNode = """{"query": "start n=node(0) foreach (x in range(1,1000) : create n={id:x}) return count(*) "}"""

  val scn = scenario("Create 10M Nodes")
    .repeat(1000) {
    exec(
      http("create node")
        .post("/db/data/cypher")
        .body(createNode)
        .asJSON
        .check(status.is(200)))
      .pause(0 milliseconds, 5 milliseconds)
  }


  setUp(
    scn.users(10).ramp(10).protocolConfig(httpConf)
  )
}
