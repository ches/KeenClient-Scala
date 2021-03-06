package io.keen.client.scala

import java.util.concurrent.{ Executors, ScheduledThreadPoolExecutor, TimeUnit }

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.collection.concurrent.TrieMap
import scala.collection.mutable.ListBuffer

import com.typesafe.config.{ Config, ConfigFactory }
import grizzled.slf4j.Logging

// XXX Remaining: Funnel, Saved Queries List, Saved Queries Row, Saved Queries Row Result
// Event deletion

class Client(
    config: Config = ConfigFactory.load(),
    // These will move to a config file:
    val scheme: String = "https",
    val authority: String = "api.keen.io",
    val version: String = "3.0"
) extends HttpAdapterComponent with Logging {

  val httpAdapter: HttpAdapter = new HttpAdapterSpray
  val settings: Settings = new Settings(config)

  // TODO: This is a smell, see http://12factor.net/config
  val environment: Option[String] = settings.environment

  /**
   * Disconnects any remaining connections. Both idle and active. If you are accessing
   * Keen through a proxy that keeps connections alive this is useful.
   */
  def shutdown() = {
    httpAdapter.shutdown()
  }
}

/**
 * A [[Client]] can mix in one or more `AccessLevel`s to enable API calls for
 * read, write, and master operations.
 *
 * The intention of this approach is to make it a compile-time error to call an
 * API method requiring a write key if you haven't statically declared that the
 * client should be a writer, for example.
 *
 * This also means that runtime checks for presence of optional settings (keys
 * for access levels you don't need) are pushed up to the time of client
 * instantiation: if you've forgotten to provide a write key in your deployment
 * environment, we won't wait to throw a runtime exception at the point that you
 * make a write call, perhaps long after your app has started and you've gone
 * home for the weekend.
 *
 * @example Client with read and write access:
 * {{{
 * val keen = new Client with Reader with Writer
 * }}}
 *
 * @see [[https://keen.io/docs/security/]]
 */
sealed protected trait AccessLevel {
  // Access levels need the basic facilities of a Client; require that.
  self: Client =>

  val projectId: String = settings.projectId

  // TODO: These don't belong here abstraction-wise. They will move to a config
  // file and will be properties of HttpAdapter so that doRequest does not need
  // to be defined in this trait just to pass these through. The other traits
  // will then be able to use httpAdapter.doRequest instead of this inherited
  // version.
  val scheme: String
  val authority: String
  val version: String

  protected def doRequest(
    path: String,
    method: String,
    key: String,
    body: Option[String] = None,
    params: Map[String, Option[String]] = Map.empty
  ) = {

    httpAdapter.doRequest(method = method, scheme = scheme, authority = authority, path = path, key = key, body = body, params = params)
  }
}

/**
 * A [[Client]] mixing in `Reader` can make Keen IO API calls requiring a read
 * key.
 *
 * A read key must be configured in the `Client`'s [[Settings]] or the `readKey`
 * field must otherwise be set e.g. with an anonymous class override.
 *
 * @example Initializing a Client with read access
 * {{{
 * val keen = new Client with Reader {
 *   override val readKey = "myReadKey"
 * }
 * }}}
 *
 * @throws MissingCredential if a read key is not configured.
 *
 * @see [[https://keen.io/docs/security/]]
 */
trait Reader extends AccessLevel {
  self: Client =>

  /**
   * A read key required to make API calls for querying and extracting data.
   */
  val readKey: String = settings.readKey.getOrElse(throw MissingCredential("Read key required for Reader"))

  /**
   * Returns the average across all numeric values for the target property in the event collection matching the given criteria. See [[https://keen.io/docs/api/reference/#average-resource Average Resource]].
   *
   * @param collection The name of the event collection you are analyzing.
   * @param targetProperty The name of the property you are analyzing.
   * @param filters Filters are used to narrow down the events used in an analysis request based on event property values. See [[https://keen.io/docs/data-analysis/filters/ Filters]].
   * @param timeframe A Timeframe specifies the events to use for analysis based on a window of time. If no timeframe is specified, all events will be counted. See [[https://keen.io/docs/data-analysis/timeframe/ Timeframes]].
   * @param timezone Modifies the timeframe filters for Relative Timeframes to match a specific timezone.
   * @param groupBy The group_by parameter specifies the name of a property by which you would like to group the results. Using this parameter changes the response format. See [[https://keen.io/docs/data-analysis/group-by/ Group By]].
   */
  def average(
    collection: String,
    targetProperty: String,
    filters: Option[String] = None,
    timeframe: Option[String] = None,
    timezone: Option[String] = None,
    groupBy: Option[String] = None
  ): Future[Response] =

    doQuery(
      analysisType = "average",
      collection = collection,
      targetProperty = Some(targetProperty),
      filters = filters,
      timeframe = timeframe,
      timezone = timezone,
      groupBy = groupBy
    )

  /**
   * Returns the number of resources in the event collection matching the given criteria. See [[https://keen.io/docs/api/reference/#event-resource Event Resource]].
   *
   * @param collection The name of the event collection you are analyzing.
   * @param filters Filters are used to narrow down the events used in an analysis request based on event property values. See [[https://keen.io/docs/data-analysis/filters/ Filters]].
   * @param timeframe A Timeframe specifies the events to use for analysis based on a window of time. If no timeframe is specified, all events will be counted. See [[https://keen.io/docs/data-analysis/timeframe/ Timeframes]].
   */
  def count(
    collection: String,
    filters: Option[String] = None,
    timeframe: Option[String] = None,
    timezone: Option[String] = None,
    groupBy: Option[String] = None
  ): Future[Response] =

    doQuery(
      analysisType = "count",
      collection = collection,
      targetProperty = None,
      filters = filters,
      timeframe = timeframe,
      timezone = timezone,
      groupBy = groupBy
    )

  /**
   * Returns the number of '''unique''' resources in the event collection matching the given criteria. See [[https://keen.io/docs/api/reference/#event-resource Event Resource]].
   *
   * @param collection The name of the event collection you are analyzing.
   * @param targetProperty The name of the property you are analyzing.
   * @param filters Filters are used to narrow down the events used in an analysis request based on event property values. See [[https://keen.io/docs/data-analysis/filters/ Filters]].
   * @param timeframe A Timeframe specifies the events to use for analysis based on a window of time. If no timeframe is specified, all events will be counted. See [[https://keen.io/docs/data-analysis/timeframe/ Timeframes]].
   * @param timezone Modifies the timeframe filters for Relative Timeframes to match a specific timezone.
   * @param groupBy The group_by parameter specifies the name of a property by which you would like to group the results. Using this parameter changes the response format. See [[https://keen.io/docs/data-analysis/group-by/ Group By]].
   */
  def countUnique(
    collection: String,
    targetProperty: String,
    filters: Option[String] = None,
    timeframe: Option[String] = None,
    timezone: Option[String] = None,
    groupBy: Option[String] = None
  ): Future[Response] =

    doQuery(
      analysisType = "count",
      collection = collection,
      targetProperty = Some(targetProperty),
      filters = filters,
      timeframe = timeframe,
      timezone = timezone,
      groupBy = groupBy
    )

  def extraction(
    collection: String,
    filters: Option[String] = None,
    timeframe: Option[String] = None,
    email: Option[String] = None,
    latest: Option[String] = None,
    propertyNames: Option[String] = None
  ): Future[Response] =

    doQuery(
      analysisType = "extraction",
      collection = collection,
      filters = filters,
      timeframe = timeframe,
      email = email,
      latest = latest,
      propertyNames = propertyNames
    )

  /**
   * Returns the maximum numeric value for the target property in the event collection matching the given criteria. See [[https://keen.io/docs/api/reference/#maximum-resource Maximum Resource]].
   *
   * @param collection The name of the event collection you are analyzing.
   * @param targetProperty The name of the property you are analyzing.
   * @param filters Filters are used to narrow down the events used in an analysis request based on event property values. See [[https://keen.io/docs/data-analysis/filters/ Filters]].
   * @param timeframe A Timeframe specifies the events to use for analysis based on a window of time. If no timeframe is specified, all events will be counted. See [[https://keen.io/docs/data-analysis/timeframe/ Timeframes]].
   * @param timezone Modifies the timeframe filters for Relative Timeframes to match a specific timezone.
   * @param groupBy The group_by parameter specifies the name of a property by which you would like to group the results. Using this parameter changes the response format. See [[https://keen.io/docs/data-analysis/group-by/ Group By]].
   */
  def maximum(
    collection: String,
    targetProperty: String,
    filters: Option[String] = None,
    timeframe: Option[String] = None,
    timezone: Option[String] = None,
    groupBy: Option[String] = None
  ): Future[Response] =

    doQuery(
      analysisType = "maximum",
      collection = collection,
      targetProperty = Some(targetProperty),
      filters = filters,
      timeframe = timeframe,
      timezone = timezone,
      groupBy = groupBy
    )

  /**
   * Returns the minimum numeric value for the target property in the event collection matching the given criteria. See [[https://keen.io/docs/api/reference/#minimum-resource Minimum Resource]].
   *
   * @param collection The name of the event collection you are analyzing.
   * @param targetProperty The name of the property you are analyzing.
   * @param filters Filters are used to narrow down the events used in an analysis request based on event property values. See [[https://keen.io/docs/data-analysis/filters/ Filters]].
   * @param timeframe A Timeframe specifies the events to use for analysis based on a window of time. If no timeframe is specified, all events will be counted. See [[https://keen.io/docs/data-analysis/timeframe/ Timeframes]].
   * @param timezone Modifies the timeframe filters for Relative Timeframes to match a specific timezone.
   * @param groupBy The group_by parameter specifies the name of a property by which you would like to group the results. Using this parameter changes the response format. See [[https://keen.io/docs/data-analysis/group-by/ Group By]].
   */
  def minimum(
    collection: String,
    targetProperty: String,
    filters: Option[String] = None,
    timeframe: Option[String] = None,
    timezone: Option[String] = None,
    groupBy: Option[String] = None
  ): Future[Response] =

    doQuery(
      analysisType = "minimum",
      collection = collection,
      targetProperty = Some(targetProperty),
      filters = filters,
      timeframe = timeframe,
      timezone = timezone,
      groupBy = groupBy
    )

  /**
   * Returns a list of '''unique''' resources in the event collection matching the given criteria. See [[https://keen.io/docs/api/reference/#select-unique-resource Select Unique Resource]].
   *
   * @param collection The name of the event collection you are analyzing.
   * @param targetProperty The name of the property you are analyzing.
   * @param filters Filters are used to narrow down the events used in an analysis request based on event property values. See [[https://keen.io/docs/data-analysis/filters/ Filters]].
   * @param timeframe A Timeframe specifies the events to use for analysis based on a window of time. If no timeframe is specified, all events will be counted. See [[https://keen.io/docs/data-analysis/timeframe/ Timeframes]].
   * @param timezone Modifies the timeframe filters for Relative Timeframes to match a specific timezone.
   * @param groupBy The group_by parameter specifies the name of a property by which you would like to group the results. Using this parameter changes the response format. See [[https://keen.io/docs/data-analysis/group-by/ Group By]].
   */
  def selectUnique(
    collection: String,
    targetProperty: String,
    filters: Option[String] = None,
    timeframe: Option[String] = None,
    timezone: Option[String] = None,
    groupBy: Option[String] = None
  ): Future[Response] =

    doQuery(
      analysisType = "select_unique",
      collection = collection,
      targetProperty = Some(targetProperty),
      filters = filters,
      timeframe = timeframe,
      timezone = timezone,
      groupBy = groupBy
    )

  /**
   * Returns the sum across all numeric values for the target property in the event collection matching the given criteria. See [[https://keen.io/docs/api/reference/#sum-resource Sum Resource]].
   *
   * @param collection The name of the event collection you are analyzing.
   * @param targetProperty The name of the property you are analyzing.
   * @param filters Filters are used to narrow down the events used in an analysis request based on event property values. See [[https://keen.io/docs/data-analysis/filters/ Filters]].
   * @param timeframe A Timeframe specifies the events to use for analysis based on a window of time. If no timeframe is specified, all events will be counted. See [[https://keen.io/docs/data-analysis/timeframe/ Timeframes]].
   * @param timezone Modifies the timeframe filters for Relative Timeframes to match a specific timezone.
   * @param groupBy The group_by parameter specifies the name of a property by which you would like to group the results. Using this parameter changes the response format. See [[https://keen.io/docs/data-analysis/group-by/ Group By]].
   */
  def sum(
    collection: String,
    targetProperty: String,
    filters: Option[String] = None,
    timeframe: Option[String] = None,
    timezone: Option[String] = None,
    groupBy: Option[String] = None
  ): Future[Response] =

    doQuery(
      analysisType = "sum",
      collection = collection,
      targetProperty = Some(targetProperty),
      filters = filters,
      timeframe = timeframe,
      timezone = timezone,
      groupBy = groupBy
    )

  private def doQuery(
    analysisType: String,
    collection: String,
    targetProperty: Option[String] = None,
    filters: Option[String] = None,
    timeframe: Option[String] = None,
    timezone: Option[String] = None,
    groupBy: Option[String] = None,
    email: Option[String] = None,
    latest: Option[String] = None,
    propertyNames: Option[String] = None
  ): Future[Response] = {

    val path = Seq(version, "projects", projectId, "queries", analysisType).mkString("/")

    val params = Map(
      "event_collection" -> Some(collection),
      "target_property" -> targetProperty,
      "filters" -> filters,
      "timeframe" -> timeframe,
      "timezone" -> timezone,
      "group_by" -> groupBy,
      "email" -> email,
      "latest" -> latest,
      "property_names" -> propertyNames
    )

    doRequest(path = path, method = "GET", key = readKey, params = params)
  }
}

/**
 * A [[Client]] mixing in `Writer` can make Keen IO API calls requiring a write
 * key.
 *
 * A write key must be configured in the `Client`'s [[Settings]] or the
 * `writeKey` field must otherwise be set e.g. with an anonymous class override.
 *
 * @example Initializing a Client with write access
 * {{{
 * val keen = new Client with Writer {
 *   override val writeKey = "myWriteKey"
 * }
 * }}}
 *
 * @throws MissingCredential if a write key is not configured.
 *
 * @see [[https://keen.io/docs/security/]]
 */
trait Writer extends AccessLevel {
  self: Client =>

  /**
   * A write key required to make API calls that write data.
   */
  val writeKey: String = settings.writeKey.getOrElse(throw MissingCredential("Write key required for Writer"))

  // constants
  // TODO: 60 seconds is an awfully long minimum…
  protected val MinSendIntervalEvents: Long = 100
  protected val MaxSendIntervalEvents: Long = 10000
  protected val MinSendInterval: FiniteDuration = 60.seconds
  protected val MaxSendInterval: FiniteDuration = 1.hour

  // initialize and configure our local event store queue
  val batchSize: Integer = settings.batchSize
  val batchTimeout: FiniteDuration = settings.batchTimeout
  val eventStore: EventStore = new RamEventStore
  eventStore.maxEventsPerCollection = settings.maxEventsPerCollection
  val sendIntervalEvents: Integer = settings.sendIntervalEvents
  val sendInterval: FiniteDuration = settings.sendIntervalDuration
  val shutdownDelay: FiniteDuration = settings.shutdownDelay

  /**
   * Schedule sending of queued events.
   */
  protected val scheduledThreadPool: Option[ScheduledThreadPoolExecutor] = scheduleSendQueuedEvents()

  /**
   * Publish a single event. See [[https://keen.io/docs/api/reference/#event-collection-resource Event Collection Resource]].
   *
   * @param collection The collection to which the event will be added.
   * @param event The event
   */
  def addEvent(collection: String, event: String): Future[Response] = {
    val path = Seq(version, "projects", projectId, "events", collection).mkString("/")
    doRequest(path = path, method = "POST", key = writeKey, body = Some(event))
  }

  /**
   * Publish multiple events. See [[https://keen.io/docs/api/reference/#event-resource Event Resource]].
   *
   * @param events The events to add to the project.
   */
  def addEvents(events: String): Future[Response] = {
    val path = Seq(version, "projects", projectId, "events").mkString("/")
    doRequest(path = path, method = "POST", key = writeKey, body = Some(events))
  }

  /**
   * Queue events locally for subsequent publishing.
   *
   * @param collection The collection to which the event will be added.
   * @param event The event
   */
  def queueEvent(collection: String, event: String): Unit = {
    // bypass min/max intervals for testing
    // FIXME: There are less kludgey ways to achieve testability here
    environment match {
      case Some("test") if Some("test").get matches "(?i)test" =>
      case _ =>
        require(
          sendIntervalEvents == 0 || (sendIntervalEvents >= MinSendIntervalEvents && sendIntervalEvents <= MaxSendIntervalEvents),
          s"Send events interval must be between $MinSendIntervalEvents and $MaxSendIntervalEvents"
        )
    }

    // write the event to the queue
    eventStore.store(projectId, collection, event)

    // check the queue to see if we meet or exceed keen.optional.queue.send-interval.events. if so, send
    // all queued events
    if (sendIntervalEvents != 0) {
      if (eventStore.size >= sendIntervalEvents) {
        sendQueuedEvents()
      }
    }
  }

  /**
   * Schedules sending of queued events if keen.queue.send-interval.duration contains a valid value that
   * is between `MinSendInterval` and `MaxSendInterval`.
   */
  private def scheduleSendQueuedEvents(): Option[ScheduledThreadPoolExecutor] = {
    // bypass min/max intervals for testing
    environment match {
      case Some("test") if Some("test").get matches "(?i)test" =>
      case _ =>
        require(
          sendInterval.toSeconds == 0 || (sendInterval >= MinSendInterval && sendInterval <= MaxSendInterval),
          s"Send interval must be between $MinSendInterval and $MaxSendInterval"
        )
    }

    // send queued events every n seconds
    sendInterval.toSeconds match {
      case n if n <= 0 => None // TODO: document what config value of zero means
      case _ =>
        // use a thread pool for our scheduled threads so we can use daemon threads
        val tp = Executors.newScheduledThreadPool(1, new ClientThreadFactory).asInstanceOf[ScheduledThreadPoolExecutor]

        // schedule sending from our thread pool at a specific interval
        tp.scheduleWithFixedDelay(new Runnable {
          def run(): Unit = {
            try {
              sendQueuedEvents()
            } catch {
              case ex: Throwable =>
                error("Failed to send queued events")
                error(s"""$ex""")
            }
          }
        }, 1, sendInterval.toMillis, TimeUnit.MILLISECONDS)

        Some(tp)
    }
  }

  /**
   * Sends all queued events, removing events from the queue as events are successfully sent.
   */
  def sendQueuedEvents(): Unit = {
    val handleMap = eventStore.getHandles(projectId)
    val handles = ListBuffer.empty[Long]
    val events = ListBuffer.empty[String]

    // iterate over all of the event handles in the queue, by collection
    for ((collection, eventHandles) <- handleMap) {
      // get each event, and its handle, then add it to a buffer so we can group the events
      // into smaller batches
      for (handle <- eventHandles) {
        handles += handle
        events += eventStore.get(handle)
      }

      // group handles separately so we can use them to remove events from the queue once they've
      // been successfully added
      val handleGroup: List[ListBuffer[Long]] = handles.grouped(batchSize).toList

      // group the events by batch size, then publish them
      for ((batch, index) <- events.grouped(batchSize).zipWithIndex) {
        // publish this batch
        var response = Await.result(
          addEvents(s"""{"$collection": [${batch.mkString(",")}]}"""),
          batchTimeout
        )

        // handle addEvents responses properly
        response.statusCode match {
          case 200 | 201 =>
            // log success
            info(s"""${response.statusCode} ${response.body} | Sent ${batch.size} queued events""")

            // remove all of the handles for this batch
            for (handle <- handleGroup(index)) {
              eventStore.remove(handle)
            }

            // log removal
            info(s"""Removed ${handleGroup(index).size} events from the queue""")

          // log but DO NOT remove events from queue
          case _ => error(s"""${response.statusCode} ${response.body} | Failed to send ${batch.size} queued events""")
        }
      }
    }
  }

  /**
   * Asynchronously sends all queued events.
   */
  def sendQueuedEventsAsync(): Unit = {
    // use a thread pool for our async thread so we can use daemon threads
    val tp = Executors.newSingleThreadExecutor(new ClientThreadFactory)

    // send our queued events in a separate thread
    tp.execute(new Runnable {
      def run(): Unit = {
        sendQueuedEvents()
      }
    })
  }

  /**
   * Safely shuts down `scheduledThreadPool` before sending all events remaining in `eventStore`.
   */
  override def shutdown() = {
    // safely terminate the scheduled thread pool
    scheduledThreadPool match {
      case Some(stp) =>
        stp.shutdown()
        stp.awaitTermination(shutdownDelay.toMillis, TimeUnit.MILLISECONDS) match {
          case false => error("Failed to shutdown scheduled thread pool")
          case _     =>
        }
      case _ =>
    }

    // don't forget to empty the queue before we shutdown
    sendQueuedEvents()

    // because we're overriding Client.shutdown
    httpAdapter.shutdown()
  }
}

/**
 * A [[Client]] mixing in `Master` can make Keen IO API calls requiring a master
 * key, such as deleting data, creating saved queries, and performing
 * administrative functions.
 *
 * A `Master` client can also perform all [[Reader]] and [[Writer]] API calls
 * and does not require additional keys configured for these. However, this
 * should '''not''' be considered a shortcut! Please keep your master key as
 * secure as possible by not deploying it where it isn't strictly needed.
 *
 * A master key must be configured in the `Client`'s [[Settings]] or the
 * `masterKey` field must otherwise be set e.g. with an anonymous class override.
 *
 * @example Initializing a Client with master access
 * {{{
 * val keen = new Client with Master {
 *   override val masterKey = "myMasterKey"
 * }
 * }}}
 *
 * @throws MissingCredential if a master key is not configured.
 *
 * @see [[https://keen.io/docs/security/]]
 */
trait Master extends Reader with Writer {
  self: Client =>

  /**
   * A master key required to make API calls of administrative nature.
   */
  val masterKey: String = settings.masterKey.getOrElse(throw MissingCredential("Master key required for Master"))

  // Since a master key can perform any API call, override read and write keys
  // so that a client can extend only the Master trait when needed.
  override val readKey: String = masterKey
  override val writeKey: String = masterKey

  /**
   * Deletes the entire event collection. This is irreversible and will only work for collections under 10k events. See [[https://keen.io/docs/api/reference/#event-collection-resource Event Collection Resource]].
   *
   * @param collection The name of the collection.
   */
  def deleteCollection(collection: String): Future[Response] = {
    val path = Seq(version, "projects", projectId, "events", collection).mkString("/")
    doRequest(path = path, method = "DELETE", key = masterKey)
  }

  /**
   * Removes a property and deletes all values stored with that property name. See [[https://keen.io/docs/api/reference/#property-resource Property Resource]].
   */
  def deleteProperty(collection: String, name: String): Future[Response] = {
    val path = Seq(version, "projects", projectId, "events", collection, "properties", name).mkString("/")
    doRequest(path = path, method = "DELETE", key = masterKey)
  }

  /**
   * Returns schema information for all the event collections in this project. See [[https://keen.io/docs/api/reference/#event-resource Event Resource]].
   */
  def getEvents: Future[Response] = {
    val path = Seq(version, "projects", projectId, "events").mkString("/")
    doRequest(path = path, method = "GET", key = masterKey)
  }

  /**
   * Returns available schema information for this event collection, including properties and their type. It also returns links to sub-resources. See [[https://keen.io/docs/api/reference/#event-collection-resource Event Collection Resource]].
   *
   * @param collection The name of the collection.
   */
  def getCollection(collection: String): Future[Response] = {
    val path = Seq(version, "projects", projectId, "events", collection).mkString("/")
    doRequest(path = path, method = "GET", key = masterKey)
  }

  /**
   * Returns the projects accessible to the API user, as well as links to project sub-resources for
   * discovery. See [[https://keen.io/docs/api/reference/#projects-resource Projects Resource]].
   */
  def getProjects: Future[Response] = {
    val path = Seq(version, "projects").mkString("/")
    doRequest(path = path, method = "GET", key = masterKey)
  }

  /**
   * Returns detailed information about the specific project, as well as links to related resources.
   * See [[https://keen.io/docs/api/reference/#project-row-resource Project Row Resource]].
   */
  def getProject: Future[Response] = {
    val path = Seq(version, "projects", projectId).mkString("/")
    doRequest(path = path, method = "GET", key = masterKey)
  }

  /**
   * Returns the property name, type, and a link to sub-resources. See [[https://keen.io/docs/api/reference/#property-resource Property Resource]].
   */
  def getProperty(collection: String, name: String): Future[Response] = {
    val path = Seq(version, "projects", projectId, "events", collection, "properties", name).mkString("/")
    doRequest(path = path, method = "GET", key = masterKey)
  }

  /**
   * Returns the list of available queries and links to them. See [[https://keen.io/docs/api/reference/#queries-resource Queries Resource]].
   *
   */
  def getQueries: Future[Response] = {
    val path = Seq(version, "projects", projectId, "queries").mkString("/")
    doRequest(path = path, method = "GET", key = masterKey)
  }
}
