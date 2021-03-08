package dev.ekvedaras.laravelquery.edgeCases

import com.intellij.database.dialects.oracle.debugger.success
import com.intellij.database.util.DasUtil
import com.intellij.database.util.DbImplUtil
import com.intellij.database.util.DbUtil
import com.intellij.openapi.application.ApplicationManager
import dev.ekvedaras.laravelquery.BaseTestCase
import dev.ekvedaras.laravelquery.inspection.UnknownColumnInspection
import dev.ekvedaras.laravelquery.inspection.UnknownTableOrViewInspection

@Suppress("Deprecation")
internal class EdgeCasesTest : BaseTestCase() {
    fun testClassCastException1() {
        myFixture.configureByFile("edgeCases/classCastException1.php")
        myFixture.completeBasic()
        assertCompletion("email")
    }

    fun testClassCastException2() {
        myFixture.configureByFile("edgeCases/classCastException2.php")
        if (ApplicationManager.getApplication().isReadAccessAllowed) {
            myFixture.completeBasic()
            assertCompletion("email")
        } else {
            success(1)
        }
    }

    fun testNonQueryBuilderTableMethod() {
        val file = myFixture.configureByFile("edgeCases/nonQueryBuilderTableMethod.php")
        val schema = DasUtil.getSchemas(db).first()
        val dbSchema = DbImplUtil.findElement(DbUtil.getDataSources(project).first(), schema)
            ?: return fail("Failed to resolve DB schema")

        myFixture.completeBasic()
        assertEmpty(myFixture.lookupElementStrings?.toList() ?: listOf<String>())
        assertEmpty(myFixture.findUsages(dbSchema))
        assertInspection(file!!, UnknownTableOrViewInspection())
    }

    fun testNonQueryBuilderColumnMethod() {
        val file = myFixture.configureByFile("edgeCases/nonQueryBuilderColumnMethod.php")
        val schema = DasUtil.getSchemas(db).first()
        val dbSchema = DbImplUtil.findElement(DbUtil.getDataSources(project).first(), schema)
            ?: return fail("Failed to resolve DB schema")

        myFixture.completeBasic()
        assertEmpty(myFixture.lookupElementStrings?.toList() ?: listOf<String>())
        assertEmpty(myFixture.findUsages(dbSchema))
        assertInspection(file!!, UnknownColumnInspection())
    }

    fun testDoesNotResolvesColumnReferenceIfStringContainsDollarSign() {
        myFixture.configureByFile("edgeCases/nonCompletableArrayValue.php")
        myFixture.completeBasic()
        assertEmpty(myFixture.lookupElementStrings?.toList() ?: listOf<String>())
    }

    fun testJoinClause() {
        myFixture.configureByFile("edgeCases/joinClause.php")
        myFixture.completeBasic()
        assertCompletion("billable_id", "billable_type")
    }
}