package dev.ekvedaras.laravelquery.reference

import com.intellij.database.psi.DbPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import dev.ekvedaras.laravelquery.models.DbReferenceExpression

class ColumnPsiReference(element: PsiElement) : PsiReferenceBase<PsiElement>(element) {
    override fun resolve(): PsiElement? {
        val target = DbReferenceExpression(element, DbReferenceExpression.Companion.Type.Column)
        val tables = target.tablesAndAliases.values.map { it.first }

        rangeInElement = target.ranges.last()

//       TODO uncomment when 2021.3 comes out
//        DbUtil.getDataSources(element.project).forEach { dataSource ->
//            val dbColumn = dataSource.findElement(target.column.find { tables.contains(it.tableName) })
//            if (dbColumn != null) {
//                return dbColumn
//            }
//        }
//
//        return null

        return DbPsiFacade.getInstance(element.project).findElement(
            target.column.find { tables.contains(it.tableName) }
        )
    }
}
