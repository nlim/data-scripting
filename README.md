# Data Scripting

This Data Scripting Library is general purpose library that uses a variety of functional Scala
libraries to make writing data processing scripts easy.  It makes heavy use of scalaz.concurrent.Task.
It provides a facility for applicative command line parsing, validation, easy creation and management
of a Doobie HikariTransactor for Mysql querying, and an easy interface to the asynchronous Couchbase Client with a Task interface.  There is a function for running Dispatch's Req objects inside of Task as well.  This lets you write scripts that submit HTTP requests, like ElasticSearch queries.

With all of these facilities, and some knowledge of the Doobie, and Scalaz-Stream API's you should be able to construct data processing scripts with ease, whether your inputs are Mysql, Couchbase, ElasticSearch, HTTP, or files.

### Libraries Used

scalaz-stream, doobie, Dispatch (http://dispatch.databinder.net/Dispatch.html),
couchbase-client

### Run the Example Script

    %  sbt 'datascripting-examples/runMain datascripting.examples.Example2 --subreddits scala,haskell,business --takeAmount 100'

### Make a Jar for your ClassPath

    % sbt 'datascripting/assembly'
