/*
 * Copyright (c) 2002-2016 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.compiler.v3_1.ast

import org.neo4j.cypher.internal.compiler.v3_1.spi.{UserDefinedFunctionSignature, ProcedureReadOnlyAccess, ProcedureSignature, QualifiedProcedureName}
import org.neo4j.cypher.internal.frontend.v3_1.SemanticCheckResult._
import org.neo4j.cypher.internal.frontend.v3_1._
import org.neo4j.cypher.internal.frontend.v3_1.ast.Expression.SemanticContext
import org.neo4j.cypher.internal.frontend.v3_1.ast._
import org.neo4j.cypher.internal.frontend.v3_1.ast.functions.UnresolvedFunction
import org.neo4j.cypher.internal.frontend.v3_1.symbols._

object ResolvedUserDefinedFunctionInvocation {
  def apply(signatureLookup: QualifiedProcedureName => Option[UserDefinedFunctionSignature])(unresolved: FunctionInvocation): ResolvedUserDefinedFunctionInvocation = {
    val position = unresolved.position
    val name = QualifiedProcedureName(unresolved)
    val signature = signatureLookup(name)
    ResolvedUserDefinedFunctionInvocation(name, signature, unresolved.args)(position)
  }
}

/**
  * A ResolvedUserDefinedInvocation is a user-defined function where the signature
  * has been resolve, i.e. verified that it exists in the database
  * @param qualifiedName The qualified name of the function.
  * @param fcnSignature Either `Some(signature)` if the signature was resolved, or
  *                     `None` if the function didn't exist
  * @param callArguments The argument list to the function
  * @param position The position in the original query string.
  */
case class ResolvedUserDefinedFunctionInvocation(qualifiedName: QualifiedProcedureName,
                                                 fcnSignature: Option[UserDefinedFunctionSignature],
                                                 callArguments: IndexedSeq[Expression])
                                                (val position: InputPosition)
  extends Expression with UserDefined {

  def coerceArguments: ResolvedUserDefinedFunctionInvocation = fcnSignature match {
    case Some(signature) =>
    val optInputFields = signature.inputSignature.map(Some(_)).toStream ++ Stream.continually(None)
    val coercedArguments =
      callArguments
        .zip(optInputFields)
        .map {
          case (arg, optField) =>
            optField.map { field => CoerceTo(arg, field.typ) }.getOrElse(arg)
        }
    copy(callArguments = coercedArguments)(position)
    case None => this
  }

  override def semanticCheck(ctx: SemanticContext): SemanticCheck = fcnSignature match {
    case None => SemanticError(s"Unknown function '$qualifiedName'", position)
    case Some(signature) =>
      val expectedNumArgs = signature.inputSignature.length
      val usedDefaultArgs = signature.inputSignature.drop(callArguments.length).flatMap(_.default)
      val actualNumArgs = callArguments.length + usedDefaultArgs.length

      if (expectedNumArgs == actualNumArgs) {
        //this zip is fine since it will only verify provided args in callArguments
        //default values are checked at load time
        signature.inputSignature.zip(callArguments).map {
          case (field, arg) =>
            arg.semanticCheck(SemanticContext.Results) chain arg.expectType(field.typ.covariant)
        }.foldLeft(success)(_ chain _)
      } else {
        error(_: SemanticState,
              SemanticError(s"Function call does not provide the required number of arguments ($expectedNumArgs)",
                            position))
      }
  }

  override def containsNoUpdates = fcnSignature match {
    case None => true
    case Some(signature) => signature.accessMode match {
      case _: ProcedureReadOnlyAccess => true
      case _ => false
    }
  }
}
