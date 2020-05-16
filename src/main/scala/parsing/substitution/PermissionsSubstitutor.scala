package parsing.substitution

import domain._, utils._
import parsing.PragmaParser.Reference
import scala.util.{Try, Success, Failure}

object PermissionsSubstitutor {

  /** Combines all other `PermissionsSubstitutor` methods */
  def apply(st: SyntaxTree, ctx: PInterfaceValue): Try[Permissions] = {
    val newGlobalRules = combineUserErrorTries {
      st.permissions.globalTenant.rules
        .map(substituteAccessRule(_, None, st, ctx.value))
    }
    val substitutedRoles = st.permissions.globalTenant.roles.map { role =>
      val roleModel =
        st.models.find(model => model.id == role.user.id && model.isUser)
      roleModel match {
        case None =>
          Failure(
            UserError(
              s"User model `${role.user.id}` is not defined",
              role.position
            )
          )
        case Some(userModel) =>
          combineUserErrorTries {
            role.rules.map(
              substituteAccessRule(_, Some(userModel), st, ctx.value)
            )
          } map { newRules =>
            role.copy(rules = newRules.toSeq)
          }
      }
    }
    val newRoles = combineUserErrorTries(substitutedRoles)

    (newGlobalRules, newRoles) match {
      case (Success(rules), Success(roles)) =>
        Success {
          st.permissions.copy(
            globalTenant = st.permissions.globalTenant
              .copy(rules = rules.toSeq, roles = roles.toSeq)
          )
        }
      case (Failure(e1: UserError), Failure(e2: UserError)) =>
        Failure(UserError(e1.errors ++ e2.errors))
      case (_, Failure(err)) => Failure(err)
      case (Failure(err), _) => Failure(err)
    }
  }

  private def substituteAccessRule(
      rule: AccessRule,
      selfRole: Option[PModel],
      st: SyntaxTree,
      ctx: Map[String, PValue]
  ): Try[AccessRule] = {
    val parentName = rule.resourcePath._1.asInstanceOf[Reference].path.head
    val childRef = rule.resourcePath._2.asInstanceOf[Option[Reference]]
    val (parent, isSelfRule) =
      if (selfRole.isDefined && parentName == "self") (selfRole.get, true)
      else if (!selfRole.isDefined && parentName == "self")
        return Failure(
          UserError(
            s"`self` is not defined for rules outside a role",
            rule.position
          )
        )
      else
        (st.modelsById.get(parentName) match {
          case Some(model) => model
          case None =>
            return Failure(
              UserError(
                s"Model `${parentName}` is not defined",
                rule.position
              )
            )
        }, false)
    val child =
      if (childRef.isDefined) {
        val foundChild = childRef flatMap { ref =>
          parent.fields.find(_.id == ref.path.head)
        }
        foundChild match {
          case None =>
            return Failure(
              UserError(
                s"`${childRef.get.path.head}` is not a field of `${parent.id}`",
                rule.position
              )
            )
          case someField => someField
        }
      } else None

    val newRule = rule.copy(resourcePath = (parent, child))

    substituteRulePredicate(
      newRule,
      isSelfRule,
      st.models.toList,
      ctx
    ).flatMap(substituteAccessRulePermissions(_))
  }

  /** Used as a part of access rule substitution */
  private def substituteRulePredicate(
      rule: AccessRule,
      isSelfRule: Boolean,
      modelDefs: List[PModel],
      ctx: Map[String, PValue]
  ): Try[AccessRule] = {
    val userPredicate = rule.predicate match {
      case None => None
      case Some(ref: Reference) =>
        Substitutor.getReferencedFunction(ref, ctx) match {
          case None =>
            return Failure(UserError(s"Predicate `$ref` is not defined"))
          case somePredicate => somePredicate
        }
      case someFunction => someFunction
    }
    val withSelfAdditions = (rule.resourcePath._1, userPredicate) match {
      case (model, None) if isSelfRule =>
        Some(IfSelfAuthPredicate(modelDefs.find(_.id == model.id).get))
      case (model, Some(predicate)) if isSelfRule => {
        val selfModel = modelDefs.find(_.id == model.id).get
        Some(
          PredicateAnd(
            selfModel,
            IfSelfAuthPredicate(selfModel),
            predicate
          )
        )
      }
      case _ => userPredicate
    }

    Success(rule.copy(predicate = withSelfAdditions))
  }

  /** Used as a part of access rule substitution */
  private def substituteAccessRulePermissions(
      rule: AccessRule
  ): Try[AccessRule] = {
    import PPermission._
    val newPermissions = rule match {
      case AccessRule(_, _, permissions, _, _)
          if permissions.size > 1 && permissions.contains(All) =>
        Left(
          (
            s"`${All}` permission cannot be combined with other permissions",
            rule.position
          )
        )
      case AccessRule(_, (_, None), permissions, _, _)
          if permissions == Set(All) =>
        Right(allowedModelPermissions)
      case AccessRule(_, (_, Some(field)), permissions, _, _)
          if field.ptype.isInstanceOf[PArray] && permissions == Set(All) =>
        Right(allowedArrayFieldPermissions)
      case AccessRule(_, (_, Some(field)), permissions, _, _)
          if (field.ptype.isInstanceOf[PrimitiveType] ||
            field.ptype.isInstanceOf[PEnum]) && permissions == Set(All) =>
        Right(allowedPrimitiveFieldPermissions)
      case AccessRule(_, (_, Some(field)), permissions, _, _)
          if permissions == Set(All) =>
        Right(allowedModelPermissions)
      case AccessRule(_, (_, Some(field)), permissions, _, _)
          if field.ptype.isInstanceOf[PArray] =>
        permissions.find(!allowedArrayFieldPermissions.contains(_)) match {
          case None => Right(permissions)
          case Some(event) =>
            Left(
              (
                s"Permission `$event` cannot be specified for array field `${field.id}`",
                rule.position
              )
            )
        }
      case AccessRule(_, (_, Some(field)), permissions, _, _)
          if field.ptype.isInstanceOf[PrimitiveType] ||
            field.ptype.isInstanceOf[PEnum] =>
        permissions.find(!allowedPrimitiveFieldPermissions.contains(_)) match {
          case None => Right(permissions)
          case Some(event) =>
            Left(
              (
                s"Permission `$event` cannot be specified for primitive field `${field.id}`",
                rule.position
              )
            )
        }
      case AccessRule(_, (_, Some(field)), permissions, _, _) =>
        permissions.find(!allowedModelFieldPermissions.contains(_)) match {
          case None => Right(permissions)
          case Some(event) =>
            Left(
              (
                s"Permission `$event` cannot be specified for model field `${field.id}`",
                rule.position
              )
            )
        }
      case AccessRule(_, (model, None), permissions, _, _) =>
        permissions.find(!allowedModelPermissions.contains(_)) match {
          case None => Right(permissions)
          case Some(event) =>
            Left(
              (
                s"Permission `$event` cannot be specified for model `${model.id}`",
                rule.position
              )
            )
        }
    }

    newPermissions match {
      case Right(permissions) =>
        Success(rule.copy(permissions = permissions.toSet))
      case Left(errMsg) => Failure(UserError(errMsg :: Nil))
    }
  }
}
