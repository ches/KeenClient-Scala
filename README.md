# keen-scala

**Note**: This library is currently in development and does not implement all of the features of the Keen API.
It's safe to use for single-event publishing. Bulk publishing and other API features will be added over time.

keen-scala is based on the [dispatch](http://dispatch.databinder.net/Dispatch.html)
asynchronous HTTP library. Therefore, all of the returned values are
`Future[Response]`.

The returned object is a [Response](http://sonatype.github.io/async-http-client/apidocs/reference/com/ning/http/client/Response.html)
from the async-http-client library. Normally you'll want to use `getResponseBody`
to get the response but you can also check `getStatusCode` to verify something
didn't go awry.

## Example

```scala
import io.keen.client.scala.Client

val client = new Client

// Publish an event!
client.addEvent(
  collection = "collectionNameHere",
  event = """{"foo": "bar"}"""
)

// Publish an event and care about the result!
val resp = client.addEvent(
  collection = "collectionNameHere",
  event = """{"foo": "bar"}"""
)

// Add an onComplete callback for failures!
resp onComplete {
  case Success(resp) => println(resp.getResponseBody)
  case Failure(t) => println(t.getMessage) // A Throwable
}
```

## Get It - SBT

```
// And the resolver
resolvers += "keenlabs" at "https://raw.github.com/keenlabs/mvn-repo/master/releases/",

// Add the Dep
libraryDependencies += "keen" %% "keenclient-scala" % "0.0.1"
```

## Configuration

The client must be configured with API keys. The recommended means is through
environment variables, to avoid storing credentials in source control. To that
end, these will be used if set:

* `KEEN_PROJECT_ID`
* `KEEN_MASTER_KEY`
* `KEEN_WRITE_KEY`
* `KEEN_READ_KEY`

However, configuration uses the [Typesafe config] library -- near-ubiquitous in
the Scala ecosystem -- so you can use a config file if you must, or provide a
custom [`Config`] object programmatically for advanced needs: just pass it to
the client constructor:

```scala
import io.keen.client.scala.Client

val client = new Client(config = myCustomConfigObject)
```

To configure with a file, it must be on the classpath -- see the Typesafe config
documentation for naming and format details, and [our `reference.conf`] for
namespace and keys.

**Note**: Currently you must provide all the keys in configuration, even if you
only want to use a client in a read-only fashion. This limitation will be
removed in the future.

[Typesafe config]: https://github.com/typesafehub/config
[`Config`]: http://typesafehub.github.io/config/latest/api/com/typesafe/config/Config.html
[our `reference.conf`]: https://github.com/keenlabs/KeenClient-Scala/tree/master/src/main/resources/reference.conf

## Dependencies

Depends on [dispatch](http://dispatch.databinder.net/Dispatch.html) and
[grizzled-slf4j](http://software.clapper.org/grizzled-slf4j/). It's compiled for
scala 2.10.

## JSON

Note that at present this library does **not** do any JSON parsing. It works with strings only. It is
assumed that you will parse the JSON returned and pass stringified JSON into any methods that
require it.

## Testing

The test suite includes integration tests which require keys and access to Keen IO's
API. You can skip them with:

    test-only * -- exclude integration

Unit tests can be run with the standard SBT `test`, `testQuick`, etc.

