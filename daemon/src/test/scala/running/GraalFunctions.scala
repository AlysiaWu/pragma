package running

import org.graalvm.polyglot._
import pragma.domain.DomainImplicits.GraalValueJsonFormater
import spray.json._
import pragma.domain.SyntaxTree
import pragma.parsing.substitution.Substitutor
import pragma.domain._
import pragma.domain.PInterfaceValue
import org.scalatest.flatspec.AnyFlatSpec

class GraalFunctions extends AnyFlatSpec {
  "GraalValueJsonFormater" should "read and write Graal values correctly" in {
    val ctx = Context.create()

    val graalValue = ctx.eval(
      "js",
      """
    ({
        id: 123, 
        name: 'Stan Smith',
        pets: [
            'Haley',
            'Roger the alien'
        ],
        friends: null
    })
    """
    )
    val json = GraalValueJsonFormater.write(graalValue)
    val expected = JsObject(
      Map(
        "id" -> JsNumber(123.0),
        "name" -> JsString("Stan Smith"),
        "pets" -> JsArray(
          Vector(JsString("Haley"), JsString("Roger the alien"))
        ),
        "friends" -> JsNull
      )
    )
    assert(json == expected)

    val originalGraalValue = GraalValueJsonFormater.read(json)
    val originalAsMap =
      originalGraalValue.asHostObject[java.util.HashMap[String, Value]]
    json match {
      case JsObject(fields) => {
        fields("name") match {
          case JsString(value) =>
            assert(value == originalAsMap.get("name").asString)
          case _ => fail()
        }

        assert(fields("friends") == JsNull)

        fields("pets") match {
          case JsArray(values) =>
            assert(
              originalAsMap
                .get("pets")
                .asHostObject[Array[Value]]
                .apply(1)
                .asString
                .contains(
                  values(1)
                    .asInstanceOf[JsString]
                    .value
                )
            )
          case _ => fail()
        }
      }
      case _ => fail()
    }
  }

  "Graal Functions from different languages" should "compose" in {
    val code = """
      import "./core/src/test/scala/parsing/test-functions.js" as jsFns
      import "./core/src/test/scala/parsing/test_functions.py" as pyFns
    """
    val st = SyntaxTree.from(code).get
    val graalCtx = Context.create()
    val ctx = Substitutor.getContext(st.imports, graalCtx).get
    (
      ctx.value("jsFns").asInstanceOf[PInterfaceValue].value("f"),
      ctx.value("pyFns").asInstanceOf[PInterfaceValue].value("increment")
    ) match {
      case (jsFn: GraalFunction, pyFn: GraalFunction) => {
        val jsFnOf1 = jsFn.execute(JsNumber(1)).get
        val pyFnOfJsFnOf1 = pyFn.execute(jsFnOf1).get
        assert(pyFnOfJsFnOf1 == JsNumber(3))
      }
      case _ => fail()
    }
  }
}
