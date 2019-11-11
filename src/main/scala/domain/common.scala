package domain
import utils._, primitives._
import org.parboiled2.Position

/**
  * An HType is a data representation (models, enums, and primitive types)
  */
trait HType

// Base case for recursive types
case class HSelf(id: String) extends HType with Identifiable

case class HReference(id: String) extends HType with Identifiable

sealed trait HConstruct extends Positioned

case class SyntaxTree(
    constants: List[HConst],
    imports: List[HImport],
    models: List[HModel],
    enums: List[HEnum],
    permissions: Permissions
) {
  def findTypeById(id: String): Option[HType] =
    models.find(model => model.id == id) orElse
      enums.find(enum => enum.id == id)
}

case class PositionRange(start: Position, end: Position)

trait Positioned {
  val position: Option[PositionRange]
}

case class HConst(id: String, value: HValue, position: Option[PositionRange])
    extends Identifiable
    with HConstruct

case class HImport(
    id: String,
    filePath: String,
    position: Option[PositionRange]
) extends HConstruct
    with Identifiable
    with Positioned

trait HShape extends Identifiable with HConstruct {
  override val id: String
  val fields: List[HShapeField]
}

case class HModel(
    id: String,
    fields: List[HModelField],
    directives: List[ModelDirective],
    position: Option[PositionRange]
) extends HType
    with HShape {
  lazy val isUser = directives.exists(d => d.id == "user")
  lazy val primaryField = fields
    .find(f => f.directives.exists(d => d.id == "primary"))
    .get
}

case class HInterface(
    id: String,
    fields: List[HInterfaceField],
    position: Option[PositionRange]
) extends HType
    with HShape

trait HShapeField extends Positioned with Identifiable {
  val htype: HType
  def isOptional = htype match {
    case HOption(_) => true
    case _          => false
  }
}

case class HModelField(
    id: String,
    htype: HType,
    defaultValue: Option[HValue],
    directives: List[FieldDirective],
    position: Option[PositionRange]
) extends HShapeField

case class HInterfaceField(
    id: String,
    htype: HType,
    position: Option[PositionRange]
) extends HShapeField

sealed trait Directive extends Identifiable {
  val id: String
  val args: HInterfaceValue
}
object Directive {
  def modelDirectives(self: HModel) = Map(
    "validate" -> HInterface(
      "validate",
      List(HInterfaceField("validator", self, None)),
      None
    ),
    "user" -> HInterface("user", Nil, None),
    "plural" -> HInterface(
      "plural",
      List(HInterfaceField("name", HString, None)),
      None
    )
  )

  def fieldDirectives(model: HModel, field: HModelField) = Map(
    "set" -> HInterface(
      "set",
      HInterfaceField("self", model, None) ::
        HInterfaceField("new", field.htype, None) :: Nil,
      None
    ),
    "get" -> HInterface(
      "get",
      HInterfaceField("self", model, None) :: Nil,
      None
    ),
    "id" -> HInterface("id", Nil, None),
    "unique" -> HInterface("unique", Nil, None)
  )
}

case class ModelDirective(
    id: String,
    args: HInterfaceValue,
    position: Option[PositionRange]
) extends Directive

case class FieldDirective(
    id: String,
    args: HInterfaceValue,
    position: Option[PositionRange]
) extends Directive

case class ServiceDirective(
    id: String,
    args: HInterfaceValue,
    position: Option[PositionRange]
) extends Directive

case class HEnum(
    id: String,
    values: List[String],
    position: Option[PositionRange]
) extends Identifiable
    with HType
    with HConstruct

sealed trait HEvent
case object Read extends HEvent
case object Create extends HEvent
case object Update extends HEvent
case object Delete extends HEvent
case object All extends HEvent

case class Permissions(
    globalTenant: Tenant,
    tenents: List[Tenant],
    position: Option[PositionRange]
) extends HConstruct

case class Tenant(
    id: String,
    rules: List[AccessRule],
    position: Option[PositionRange]
) extends Identifiable
    with Positioned

sealed trait AccessRule {
  val resource: Resource
  val actions: List[HEvent]
  val predicate: HFunctionValue
  val position: Option[PositionRange]
}

case class RoleBasedRule(
    user: HModel,
    resource: Resource,
    actions: List[HEvent],
    predicate: HFunctionValue,
    position: Option[PositionRange]
) extends AccessRule
    with Positioned

case class GlobalRule(
    resource: Resource,
    actions: List[HEvent],
    predicate: HFunctionValue,
    position: Option[PositionRange]
) extends AccessRule
    with Positioned

trait Resource {
  val shape: HShape
}

case class ShapeResource(shape: HShape) extends Resource
case class FieldResource(field: HShapeField, shape: HShape)

case class HConfig(values: List[ConfigEntry], position: Option[PositionRange])
    extends HConstruct

case class ConfigEntry(
    key: String,
    value: HValue,
    position: Option[PositionRange]
) extends Positioned
