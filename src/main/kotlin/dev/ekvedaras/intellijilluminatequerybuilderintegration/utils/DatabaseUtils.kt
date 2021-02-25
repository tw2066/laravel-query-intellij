package dev.ekvedaras.intellijilluminatequerybuilderintegration.utils

import com.intellij.database.model.DasColumn
import com.intellij.database.model.DasNamespace
import com.intellij.database.model.DasTable
import com.intellij.database.model.ObjectKind
import com.intellij.database.psi.DbDataSource
import com.intellij.database.util.DasUtil
import com.intellij.database.util.DbUtil
import com.intellij.openapi.project.Project
import java.util.stream.Stream

class DatabaseUtils {
    companion object {
        fun Project.dbDataSourcesInParallel(): Stream<out DbDataSource> =
            DbUtil.getDataSources(this).toList().parallelStream()

        fun DbDataSource.schemasInParallel(): Stream<out DasNamespace> =
            DasUtil.getSchemas(this).toList().parallelStream()

        fun DbDataSource.tables() =
            DasUtil.getTables(this)

        fun DbDataSource.tablesInParallel(): Stream<out DasTable> =
            DasUtil.getTables(this).toList().parallelStream()

        fun DasNamespace.tablesInParallel(): Stream<out DasTable> =
            this.getDasChildren(ObjectKind.TABLE).toList().parallelStream().map { it as DasTable }

        fun DasTable.columnsInParallel(): Stream<out DasColumn> =
            this.getDasChildren(ObjectKind.COLUMN).toList().parallelStream().map { it as DasColumn }
    }
}