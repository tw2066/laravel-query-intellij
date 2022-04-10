package dev.ekvedaras.laravelquery.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.database.model.DasTable
import com.intellij.database.model.ObjectKind
import com.intellij.database.util.DbUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.Variable
import com.jetbrains.php.lang.psi.elements.impl.VariableImpl
import com.jetbrains.rd.util.first
import dev.ekvedaras.laravelquery.models.DbReferenceExpression
import dev.ekvedaras.laravelquery.services.LaravelQuerySettings
import dev.ekvedaras.laravelquery.utils.BlueprintMethod.Companion.createsTable
import dev.ekvedaras.laravelquery.utils.BlueprintMethod.Companion.getColumnDefinitionReference
import dev.ekvedaras.laravelquery.utils.BlueprintMethod.Companion.getColumnName
import dev.ekvedaras.laravelquery.utils.BlueprintMethod.Companion.isColumnDefinition
import dev.ekvedaras.laravelquery.utils.BlueprintMethod.Companion.isId
import dev.ekvedaras.laravelquery.utils.BlueprintMethod.Companion.isInsideUpMigration
import dev.ekvedaras.laravelquery.utils.BlueprintMethod.Companion.isNullable
import dev.ekvedaras.laravelquery.utils.BlueprintMethod.Companion.isSoftDeletes
import dev.ekvedaras.laravelquery.utils.BlueprintMethod.Companion.isTimestamps
import dev.ekvedaras.laravelquery.utils.DatabaseUtils.Companion.tables
import dev.ekvedaras.laravelquery.utils.LaravelUtils.Companion.isBlueprintMethod
import dev.ekvedaras.laravelquery.utils.LaravelUtils.Companion.isInsideRegularFunction
import dev.ekvedaras.laravelquery.utils.MethodUtils
import dev.ekvedaras.laravelquery.utils.PsiUtils.Companion.references
import dev.ekvedaras.laravelquery.utils.SchemaMethod.Companion.blueprintTableParam
import dev.ekvedaras.laravelquery.utils.SchemaMethod.Companion.statementsForTable
import icons.DatabaseIcons
import java.util.Collections

class NewMigrationCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val method = MethodUtils.resolveMethodReference(parameters.position) ?: return
        val project = method.project

        if (shouldNotComplete(project, method, parameters)) {
            return
        }

        val target = DbReferenceExpression(parameters.position, DbReferenceExpression.Companion.Type.Column)
        val items = Collections.synchronizedList(mutableListOf<LookupElement>())

        var table: DasTable? = null;

        DbUtil.getDataSources(project).filter {
            LaravelQuerySettings.getInstance(project).interestedIn(it)
        }.forEach { dataSource ->
            val dasTable = dataSource.tables().firstOrNull { it.name == target.tablesAndAliases.first().key }
            if (dasTable != null) {
                table = dasTable
                return@forEach
            }
        }

        val columns = table?.getDasChildren(ObjectKind.COLUMN)?.map { it.name } ?: listOf<String>()
        val indexes = table?.getDasChildren(ObjectKind.INDEX)?.map { it.name } ?: listOf<String>()
        val keys = table?.getDasChildren(ObjectKind.KEY)?.map { it.name } ?: listOf<String>()
        val foreignKeys = table?.getDasChildren(ObjectKind.FOREIGN_KEY)?.map { it.name } ?: listOf<String>()

        if (ApplicationManager.getApplication().isReadAccessAllowed) {
            ApplicationManager.getApplication().runReadAction {
                method.parentOfType<PhpClass>()?.ownMethods?.forEach { migrationMethod ->
                    if (migrationMethod.name == "up") {
                        migrationMethod.statementsForTable(target.tablesAndAliases.first().key).forEach { statementMethod ->
                            statementMethod.blueprintTableParam()?.references()?.forEach referenceLoop@{ reference ->
                                val referenceMethod = (reference.element as Variable).parent as MethodReference

                                if (referenceMethod == method) { // TODO: not working like this
                                    return@referenceLoop
                                }

                                if (method.isInsideUpMigration() && method.createsTable() && method.isColumnDefinition()) {
                                    return@referenceLoop
                                }

                                if (referenceMethod.isId() && !columns.contains("id")) {
                                    items.add(
                                        LookupElementBuilder
                                            .create("id")
                                            .withIcon(DatabaseIcons.ColGoldKey)
                                            .withTypeText("primary")
                                            .withPsiElement(referenceMethod)
                                    )
                                } else if (referenceMethod.isTimestamps()) {
                                    if (!columns.contains("created_at")) {
                                        items.add(
                                            LookupElementBuilder
                                                .create("created_at")
                                                .withIcon(DatabaseIcons.ColDot)
                                                .withTypeText(referenceMethod.name)
                                                .withPsiElement(referenceMethod)
                                        )
                                    }

                                    if (!columns.contains("updated_at")) {
                                        items.add(
                                            LookupElementBuilder
                                                .create("updated_at")
                                                .withIcon(DatabaseIcons.ColDot)
                                                .withTypeText(referenceMethod.name)
                                                .withPsiElement(referenceMethod)
                                        )
                                    }
                                } else if (referenceMethod.isSoftDeletes() && !columns.contains("deleted_at")) {
                                    items.add(
                                        LookupElementBuilder
                                            .create("deleted_at")
                                            .withIcon(DatabaseIcons.Col)
                                            .withTypeText("timestamp")
                                            .withPsiElement(referenceMethod)
                                    )
                                } else if (referenceMethod.isColumnDefinition() && !columns.contains(referenceMethod.getColumnName())) {
                                    items.add(
                                        LookupElementBuilder
                                            .create(referenceMethod.getColumnName() ?: '?')
                                            .withIcon(
                                                if (referenceMethod.isNullable()) {
                                                    DatabaseIcons.Col
                                                } else {
                                                    DatabaseIcons.ColDot
                                                }
                                            )
                                            .withTypeText(referenceMethod.name)
                                            .withPsiElement(referenceMethod.getColumnDefinitionReference())
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        result.addAllElements(
            items.distinctBy { it.lookupString }
        )

        result.stopHere()
    }

    private fun shouldNotComplete(project: Project, method: MethodReference, parameters: CompletionParameters) =
        !ApplicationManager.getApplication().isReadAccessAllowed ||
            !method.isBlueprintMethod(project) ||
            parameters.isInsideRegularFunction() ||
            method.firstPsiChild !is VariableImpl
}
