package running

import org.scalatest._
import running.pipeline.functions.Authorizer
import running.pipeline.Request
import sangria.macros._
import spray.json._
import domain.SyntaxTree
import setup.storage.MockStorage
import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.util._

class Authorization extends FlatSpec {
  "Authorizer" should "authorize requests correctly" in {
    val code = """
    @user
    model User {
      username: String @primary @publicCredential
      password: String @secretCredential
      isVerified: Boolean = false
    }

    allow CREATE User
    deny UPDATE User.isVerified
    """

    val syntaxTree = SyntaxTree.from(code).get
    val mockStorage = MockStorage(syntaxTree)
    val authorizer = Authorizer(syntaxTree, mockStorage, false)

    val req = Request(
      None,
      None,
      Some(JwtPaylod("123", "User")),
      gql"""
      mutation createUser {
        User {
          create(user: {
            username: "John Dow",
            password: "password",
            isVerified: true
          }) {
            username
            isVerified
          }
        }
      }
      
      query getUser {
        User {
          read(username: "John Doe") {
            username
          }
        }
      }
      """,
      Left(JsObject(Map.empty[String, JsValue])),
      Map.empty,
      "",
      ""
    )

    val result = Await.result(authorizer(req), Duration.Inf) 
    assert(result == Right(false))
  }
}
