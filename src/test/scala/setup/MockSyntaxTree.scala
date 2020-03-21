package setup

import domain._
import domain.primitives._
import scala.collection.immutable.ListMap

object MockSyntaxTree {

  val schema =
    """
  @user
  model Business {
    username: String? 
    email: String @publicCredential @primary
    password: String @secretCredential
    branches: [Branch] 
    mainBranch: Branch? 
    businessType: BusinessType 
  }

  model Branch {
    address: String @primary
    business: Business 
  }

  enum BusinessType {
    FOOD
    CLOTHING
    OTHER
  }"""

  val businessModel = HModel(
    "Business",
    List(
      HModelField(
        "username",
        HOption(HString),
        None,
        List(),
        None
      ),
      HModelField(
        "email",
        HString,
        None,
        List(
          Directive(
            "publicCredential",
            HInterfaceValue(ListMap(), HInterface("", List(), None)),
            FieldDirective,
            None
          ),
          Directive(
            "primary",
            HInterfaceValue(ListMap(), HInterface("", List(), None)),
            FieldDirective,
            None
          )
        ),
        None
      ),
      HModelField(
        "password",
        HString,
        None,
        List(
          Directive(
            "secretCredential",
            HInterfaceValue(ListMap(), HInterface("", List(), None)),
            FieldDirective,
            None
          )
        ),
        None
      ),
      HModelField(
        "branches",
        HArray(HReference("Branch")),
        None,
        Nil,
        None
      ),
      HModelField(
        "mainBranch",
        HOption(HReference("Branch")),
        None,
        Nil,
        None
      ),
      HModelField(
        "businessType",
        HReference("BusinessType"),
        None,
        Nil,
        None
      )
    ),
    List(
      Directive(
        "user",
        HInterfaceValue(ListMap(), HInterface("", List(), None)),
        ModelDirective,
        None
      )
    ),
    None
  )

  val branchModel =
    HModel(
      "Branch",
      List(
        HModelField(
          "address",
          HString,
          None,
          List(
            Directive(
              "primary",
              HInterfaceValue(ListMap(), HInterface("", List(), None)),
              FieldDirective,
              None
            )
          ),
          None
        ),
        HModelField(
          "business",
          HReference("Business"),
          None,
          Nil,
          None
        )
      ),
      List(),
      None
    )

  val businessTypeEnum =
    HEnum("BusinessType", List("FOOD", "CLOTHING", "OTHER"), None)
  val permissions = None

  val syntaxTree =
    SyntaxTree(
      Nil,
      List(businessModel, branchModel),
      List(businessTypeEnum),
      permissions,
      None
    )
}
