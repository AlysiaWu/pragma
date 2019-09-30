import org.scalatest._
import domain._, primitives._
import parsing._
import scala.util._
import org.parboiled2.Position

class ModelFieldTypeSubstitution extends FlatSpec {
  "Substitutor" should "substitute model field types with the defined type if found" in {
    val code = """
      enum Gender {
          Male
          Female
      }

      model User {
          username: String
          password: String
          todo: Todo
          gender: Gender
      }

      model Todo {
          title: String
          content: String
      }
      """
    val syntaxTree = new HeavenlyParser(code).syntaxTree.run().get
    val substited = new Substitutor(syntaxTree).run
    val expected = Success(
      List(
        HEnum(
          "Gender",
          List("Male", "Female"),
          Some(PositionRange(Position(12, 2, 12), Position(18, 2, 18)))
        ),
        HModel(
          "User",
          List(
            HModelField(
              "username",
              HString,
              None,
              List(),
              false,
              Some(PositionRange(Position(91, 8, 11), Position(99, 8, 19)))
            ),
            HModelField(
              "password",
              HString,
              None,
              List(),
              false,
              Some(PositionRange(Position(118, 9, 11), Position(126, 9, 19)))
            ),
            HModelField(
              "todo",
              HModel(
                "Todo",
                List(
                  HModelField(
                    "title",
                    HString,
                    None,
                    List(),
                    false,
                    Some(
                      PositionRange(
                        Position(219, 15, 11),
                        Position(224, 15, 16)
                      )
                    )
                  ),
                  HModelField(
                    "content",
                    HString,
                    None,
                    List(),
                    false,
                    Some(
                      PositionRange(
                        Position(243, 16, 11),
                        Position(250, 16, 18)
                      )
                    )
                  )
                ),
                List(),
                Some(
                  PositionRange(Position(202, 14, 13), Position(206, 14, 17))
                )
              ),
              None,
              List(),
              false,
              Some(PositionRange(Position(145, 10, 11), Position(149, 10, 15)))
            ),
            HModelField(
              "gender",
              HEnum(
                "Gender",
                List("Male", "Female"),
                Some(
                  PositionRange(Position(12, 2, 12), Position(18, 2, 18))
                )
              ),
              None,
              List(),
              false,
              Some(PositionRange(Position(166, 11, 11), Position(172, 11, 17)))
            )
          ),
          List(),
          Some(PositionRange(Position(74, 7, 13), Position(78, 7, 17)))
        ),
        HModel(
          "Todo",
          List(
            HModelField(
              "title",
              HString,
              None,
              List(),
              false,
              Some(PositionRange(Position(219, 15, 11), Position(224, 15, 16)))
            ),
            HModelField(
              "content",
              HString,
              None,
              List(),
              false,
              Some(PositionRange(Position(243, 16, 11), Position(250, 16, 18)))
            )
          ),
          List(),
          Some(PositionRange(Position(202, 14, 13), Position(206, 14, 17)))
        )
      )
    )

    assert(substited == expected)
  }

  "Substitutor" should "substitute Array and Option types" in {
    val code = """
      model Book {
        tags: [String]
        authors: [Author]
      }

      model Author {
        name: String?
      }
    """
    val parser = new HeavenlyParser(code)
    val parsedTree = parser.syntaxTree.run()
    val substitutor = new Substitutor(parsedTree.get)
    val substituted = substitutor.substituteTypes
    val expected = Success(
      List(
        HModel(
          "Book",
          List(
            HModelField(
              "tags",
              HArray(HString),
              None,
              List(),
              false,
              Some(PositionRange(Position(28, 3, 9), Position(32, 3, 13)))
            ),
            HModelField(
              "authors",
              HArray(
                HModel(
                  "Author",
                  List(
                    HModelField(
                      "name",
                      HOption(HString),
                      None,
                      List(),
                      true,
                      Some(
                        PositionRange(Position(107, 8, 9), Position(111, 8, 13))
                      )
                    )
                  ),
                  List(),
                  Some(PositionRange(Position(90, 7, 13), Position(96, 7, 19)))
                )
              ),
              None,
              List(),
              false,
              Some(PositionRange(Position(51, 4, 9), Position(58, 4, 16)))
            )
          ),
          List(),
          Some(PositionRange(Position(13, 2, 13), Position(17, 2, 17)))
        ),
        HModel(
          "Author",
          List(
            HModelField(
              "name",
              HOption(HString),
              None,
              List(),
              true,
              Some(PositionRange(Position(107, 8, 9), Position(111, 8, 13)))
            )
          ),
          List(),
          Some(PositionRange(Position(90, 7, 13), Position(96, 7, 19)))
        )
      )
    )

    assert(substituted == expected)
  }

  "Recursive types" should "be resolved correctly" in {
    val code = """
      model A {
        a: A
        as: [A]
        aOption: A?
      }
    """
    val syntaxTree = new HeavenlyParser(code).syntaxTree.run().get
    val substituted = new Substitutor(syntaxTree).substituteTypes
    val expected = Success(
      List(
        HModel(
          "A",
          List(
            HModelField(
              "a",
              HSelf,
              None,
              List(),
              false,
              Some(PositionRange(Position(25, 3, 9), Position(26, 3, 10)))
            ),
            HModelField(
              "as",
              HArray(HSelf),
              None,
              List(),
              false,
              Some(PositionRange(Position(38, 4, 9), Position(40, 4, 11)))
            ),
            HModelField(
              "aOption",
              HOption(HSelf),
              None,
              List(),
              true,
              Some(PositionRange(Position(54, 5, 9), Position(61, 5, 16)))
            )
          ),
          List(),
          Some(PositionRange(Position(13, 2, 13), Position(14, 2, 14)))
        )
      )
    )
    assert(substituted == expected)
  }
}
