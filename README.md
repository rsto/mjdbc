__mJDBC__  - Small and efficient JDBC wrapper.

* *Small:* no external dependencies. Jar size is  less than 50kb.
* *Simple:* no special configuration required. Start using it after 1 line of initialization code.
* *Reliable:* all SQL statements are parsed and validated on application startup.
* *Flexible:* switch and use native JDBC interface directly when needed.
* *Fast:* no runtime overhead when compared to JDBC.
* *Transactional:* wrap any method into transaction. Real connection is opened on first statement execution.
* *Extensible:* add support for new data types or override the way built-in types are handled.
* *Measurable:* profile timings for all SQL queries and transactions.
* *Open source:* fork and change it.
* *Bug free*... not yet... still in beta, but number of tests is growing.

[![Build Status](https://travis-ci.org/mjdbc/mjdbc.svg?branch=master)]	(https://travis-ci.org/mjdbc/mjdbc)

## Building

```bash
mvn -DskipTests=true clean package install
```

## Maven

Add snapshots repository to your pom.xml file. The project now is in alpha stage and has no public releases to Central Repository yet.
```xml
<repositories>
    <repository>
        <id>ossrh</id>
        <url>https://oss.sonatype.org/content/repositories/snapshots</url>
    </repository>
</repositories>
```

Add project dependency:
```xml
<dependency>
    <groupId>com.github.mjdbc</groupId>
    <artifactId>mjdbc</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency
```

## Brief API overview

##### Raw SQL queries
```java
    java.sql.DataSource ds = ...; // have a DataSource first. 
    Db db = Db.newInstance(ds);  // wrap DataSource with mJDBC wrapper.
    MySqlQueries q = db.attachSql(MySqlQueries.class) // attach query interface. All queries are parsed and validated at this moment.
    User user = q.getUserByLogin('login'); // run any query method
```
where MySqlQueries:
```java
public interface MySqlQueries {
    @Sql("SELECT * FROM users WHERE login = :login")
    User getUserByLogin(@Bind("login") String login)
}
```
An example of simple and fast pooled data source is [HikariCP](https://github.com/brettwooldridge/HikariCP). 

##### Transactions
To run multiple SQL queries within a single transaction create a dedicated dbi (db-interface) interface and attach it to database.
It will return a proxy class that will wrap all interface methods into transactions.
```java
    java.sql.DataSource ds = ...;
    Db db = Db.newInstance(ds);
    MyDbi dbi = db.attachDbi(MyDbiImpl(), MyDbi.class); // all MyDbi method calls will be proxied to MyDbiImpl wrapped with transactions.
    User user = dbi.getUserByLoginCreateIfNotFound('login');
```
where MyDbi:
```java
public interface MyDbi {
    User getUserByLoginCreateIfNotFound(String login);
}

public class MyDbiImpl implements MyDbi {
    public User getUserByLoginCreateIfNotFound(String login) {
        User user = myQueries.getUserByLogin(login);
        if (user == null) {
            User user = new User();
            user.login = login;
            user.id = myQueries.insertUser(user);
        }
        return user;
    }
}
```
Notes:
 * If Impl method is called directly with no use of proxy interface no new transaction is started. The method will share transaction context with an upper-stack method.
 Transaction is committed/rolled back when upper-stack method is finished.
 * All @Sql (raw SQL) methods are processed as independent transactions when no dbi interface is used.
 * No connection is allocated when Dbi method is called. The actual connection is requested from datasource only when first SQL statement is used. 
 So if DBI method uses cache and do not create any statements at all -> no network activity will be performed at all.



##### Result set mappers

Extend [DbMapper](https://github.com/mjdbc/mjdbc/blob/master/src/main/java/com/github/mjdbc/DbMapper.java) class.
It may be convenient to put the implementation into the Java class it maps [(example)](https://github.com/mjdbc/mjdbc/blob/master/src/test/java/com/github/mjdbc/test/asset/model/User.java).

```java
public static final DbMapper<User> MAPPER = (r) -> {
    User user = new User();
    user.id = new UserId(r.getInt("id"));
    user.login = r.getString("login");
    ...
    return user;
};
```

Optional: register this mapper in MJDBC interface during initialization;
```java
    Db db = Db.newInstance(ds);
    db.registerMapper(User.class, User.MAPPER)
```
Now use User type in all queries attached to MJDBC database instance.
Mappers for native Java types are supported by default [(source)](https://github.com/mjdbc/mjdbc/blob/master/src/main/java/com/github/mjdbc/util/Mappers.java) and can be overridden if needed..

Note: If mapper is not registered manually mJDBC will try to derive it from .MAPPER static field of the mapped object.

##### Parameter binders
Parameters in @Sql interfaces can be bound with @Bind or @BindBean annotations.
* @Bind maps single named parameter.
* @BindBean maps all parameters from public fields or getters of the object passed as parameter.

Binders for native Java types are supported by default [(source)](https://github.com/mjdbc/mjdbc/blob/master/src/main/java/com/github/mjdbc/util/Binders.java).

In most cases you do not need to create your own binder. All you need is to make your class to implement one of these interfaces: DbInt, DbLong or DbString [(example)](https://github.com/mjdbc/mjdbc/blob/master/src/test/java/com/github/mjdbc/test/asset/model/UserId.java).

##### Low level API
Usage of java.sql.* API is transparent in mJDBC. You can always get statements, connections, result sets and have a full power of native JDBC driver.
Example:
```java
Db db = Db.newInstance(ds);
db.execute(c -> { // wraps method into transaction
    try (java.sql.Statement statement = c.getConnection().createStatement()) {
        ...
    }
});
```
If named parameters or object/collections mappers support is needed:
```java
Db db = Db.newInstance(ds);
User user = db.execute(c -> { // wraps method into transaction
    DbStatement s = new DbStatement(c, "SELECT * FROM users WHERE login = :login", User.MAPPER)
    s.setString("login", login);

    // Direct access to JDBC starts here
    // JDBC supports parameter binding by index only. Here is the name -> indexes mapping.
    Map<String, List<Integer>> namedParametersMapping = s.parametersMapping;
    // Original JDBC PreparedStatement
    java.sql.PreparedStatement ps = s.statement;
    ... // write some low-level code with java.sql.PreparedStatement: bind data streams, execute and check result set...

    // or simply return the result using mapper class provided in DbStatement constructor.
    return s.query();
});
```

Note: that when you use [DbStatement](https://github.com/mjdbc/mjdbc/blob/master/src/main/java/com/github/mjdbc/DbStatement.java) class it is not necessary to close it manually.
It will be closed automatically when connection is closed (returned to pool). In this example  connection is closed when *db.execute()* method is finished.


##### Timers
For all methods annotated with @Sql or Dbi interface methods mjdbc automatically collects statistics:
* method invocation count
* total time spent in the method in nanoseconds.

Check [checkTxTimer/checkSqlTimer](https://github.com/mjdbc/mjdbc/blob/master/src/test/java/com/github/mjdbc/test/SamplesTest.java) tests for details.


##### More
For more examples check [unit tests](https://github.com/mjdbc/mjdbc/blob/master/src/test/java/com/github/mjdbc/test/) to see API in action.

You may also find useful to check the recommended way of writing [raw SQL interfaces](https://github.com/mjdbc/mjdbc/blob/master/src/test/java/com/github/mjdbc/test/asset/UserSql.java) and
[transactional database interfaces](https://github.com/mjdbc/mjdbc/blob/master/src/test/java/com/github/mjdbc/test/asset/dbi/SampleDbi.java) in tests.


### Requirements

Java8+


### License

This project available under Apache License 2.0.
