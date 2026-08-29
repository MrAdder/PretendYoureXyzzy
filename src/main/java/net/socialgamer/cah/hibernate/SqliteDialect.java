/*
 * The author disclaims copyright to this source code. In place of
 * a legal notice, here is a blessing:
 *
 * May you do good and not evil.
 * May you find forgiveness for yourself and forgive others.
 * May you share freely, never taking more than you give.
 *
 */
// via https://gist.github.com/virasak/54436

package net.socialgamer.cah.hibernate;

import java.sql.Types;

import org.hibernate.MappingException;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.function.SQLFunctionTemplate;
import org.hibernate.dialect.function.StandardSQLFunction;
import org.hibernate.dialect.function.VarArgsSQLFunction;
import org.hibernate.dialect.identity.IdentityColumnSupport;
import org.hibernate.dialect.identity.IdentityColumnSupportImpl;
import org.hibernate.type.StandardBasicTypes;


public class SqliteDialect extends Dialect {
  public SqliteDialect() {
    super();
    registerColumnType(Types.BIT, "integer");
    registerColumnType(Types.TINYINT, "tinyint");
    registerColumnType(Types.SMALLINT, "smallint");
    registerColumnType(Types.INTEGER, "integer");
    registerColumnType(Types.BIGINT, "bigint");
    registerColumnType(Types.FLOAT, "float");
    registerColumnType(Types.REAL, "real");
    registerColumnType(Types.DOUBLE, "double");
    registerColumnType(Types.NUMERIC, "numeric");
    registerColumnType(Types.DECIMAL, "decimal");
    registerColumnType(Types.CHAR, "char");
    registerColumnType(Types.VARCHAR, "varchar");
    registerColumnType(Types.LONGVARCHAR, "longvarchar");
    registerColumnType(Types.DATE, "date");
    registerColumnType(Types.TIME, "time");
    registerColumnType(Types.TIMESTAMP, "timestamp");
    registerColumnType(Types.BINARY, "blob");
    registerColumnType(Types.VARBINARY, "blob");
    registerColumnType(Types.LONGVARBINARY, "blob");
    // registerColumnType(Types.NULL, "null");
    registerColumnType(Types.BLOB, "blob");
    registerColumnType(Types.CLOB, "clob");
    registerColumnType(Types.BOOLEAN, "integer");

    registerFunction("concat", new VarArgsSQLFunction(StandardBasicTypes.STRING, "", "||", ""));
    registerFunction("mod", new SQLFunctionTemplate(StandardBasicTypes.INTEGER, "?1 % ?2"));
    registerFunction("substr", new StandardSQLFunction("substr", StandardBasicTypes.STRING));
    registerFunction("substring", new StandardSQLFunction("substr", StandardBasicTypes.STRING));
  }

  /*
   * Hibernate 5.1+ moved identity-column handling off Dialect and onto a separate
   * IdentityColumnSupport strategy object (getIdentityColumnString()/getIdentitySelectString()
   * also picked up extra table/column/type parameters they don't need here).
   */
  @Override
  public IdentityColumnSupport getIdentityColumnSupport() {
    return new IdentityColumnSupportImpl() {
      @Override
      public boolean supportsIdentityColumns() {
        return true;
      }

      @Override
      public boolean hasDataTypeInIdentityColumn() {
        return false; // As specify in NHibernate dialect
      }

      @Override
      public String getIdentityColumnString(final int type) throws MappingException {
        // return "integer primary key autoincrement";
        return "integer";
      }

      @Override
      public String getIdentitySelectString(final String table, final String column,
          final int type) throws MappingException {
        return "select last_insert_rowid()";
      }
    };
  }

  @Override
  public boolean supportsLimit() {
    return true;
  }

  @Override
  public String getLimitString(final String query, final boolean hasOffset) {
    return new StringBuffer(query.length() + 20).append(query)
        .append(hasOffset ? " limit ? offset ?" : " limit ?").toString();
  }

  @Override
  public boolean supportsCurrentTimestampSelection() {
    return true;
  }

  @Override
  public boolean isCurrentTimestampSelectStringCallable() {
    return false;
  }

  @Override
  public String getCurrentTimestampSelectString() {
    return "select current_timestamp";
  }

  @Override
  public boolean supportsUnionAll() {
    return true;
  }

  @Override
  public boolean hasAlterTable() {
    return false; // As specify in NHibernate dialect
  }

  @Override
  public boolean dropConstraints() {
    return false;
  }

  @Override
  public String getAddColumnString() {
    return "add column";
  }

  @Override
  public String getForUpdateString() {
    return "";
  }

  @Override
  public boolean supportsOuterJoinForUpdate() {
    return false;
  }

  @Override
  public String getDropForeignKeyString() {
    throw new UnsupportedOperationException(
        "No drop foreign key syntax supported by SQLiteDialect");
  }

  @Override
  public String getAddForeignKeyConstraintString(final String constraintName,
      final String[] foreignKey,
      final String referencedTable, final String[] primaryKey,
      final boolean referencesPrimaryKey) {
    throw new UnsupportedOperationException(
        "No add foreign key syntax supported by SQLiteDialect");
  }

  @Override
  public String getAddPrimaryKeyConstraintString(final String constraintName) {
    throw new UnsupportedOperationException(
        "No add primary key syntax supported by SQLiteDialect");
  }

  @Override
  public boolean supportsIfExistsBeforeTableName() {
    return true;
  }

@Override
  public boolean supportsCascadeDelete() {
    return false;
  }
}
