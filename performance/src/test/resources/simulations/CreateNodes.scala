package simulations

import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.http.Predef._
import akka.util.duration._
import bootstrap._

class CreateNodes extends Simulation {

  val httpConf = httpConfig
    .baseURL("http://localhost:7474")
    .acceptHeader("application/json")

  val createNode = """{"query": "create me"}"""

  val scn = scenario("Create Nodes")
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
    scn.users(100).ramp(10).protocolConfig(httpConf)
  )
}
