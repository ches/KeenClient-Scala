package io.keen.client.scala

import scala.concurrent.Future

import com.typesafe.config._
import grizzled.slf4j.Logging

// XXX Remaining: Extraction, Funnel, Saved Queries List, Saved Queries Row, Saved Queries Row Result
// Event deletion

// TODO: We should allow some settings to be optional -- read-only clients, no
// master operations allowed, etc. But we'd need to wrap return types in Try to
// express error conditions that can happen before a request is even attempted.
class Settings(config: Config) {
  config.checkValid(ConfigFactory.defaultReference(), "keen")

  val apiHost    = config.getString("keen.api-host")
  val apiPort    = config.getInt("keen.api-port")
  val apiVersion = config.getString("keen.api-version")

  val masterKey = config.getString("keen.master-key")
  val projectId = config.getString("keen.project-id")
  val readKey   = config.getString("keen.read-key")
  val writeKey  = config.getString("keen.write-key")
}

// XXX These should probably be Options with handling for missing ones below.
class Client(config: Config = ConfigFactory.load()) extends HttpAdapterComponent with Logging {

  val settings = new Settings(config)
  val httpAdapter: HttpAdapter = new SprayHttpAdapter(settings.apiHost, settings.apiPort)
  val version = settings.apiVersion

  /**
   * Publish a single event. See [[https://keen.io/docs/api/reference/#event-collection-resource Event Collection Resource]].
   *
   * @param collection The collection to which the event will be added.
   * @param event The event
   */
  def addEvent(collection: String, event: String): Future[Response] = {
    val path = Seq(version, "projects", settings.projectId, "events", collection).mkString("/")
    httpAdapter.post(path = path, authKey = settings.writeKey, body = Some(event))
  }

  /**
   * Publish multiple events. See [[https://keen.io/docs/api/reference/#event-resource Event Resource]].
   *
   * @param events The events to add to the project.
   */
  def addEvents(events: String): Future[Response] = {
    val path = Seq(version, "projects", settings.projectId, "events").mkString("/")
    httpAdapter.post(path = path, authKey = settings.writeKey, body = Some(events))
  }

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
    groupBy: Option[String]= None): Future[Response] =

    doQuery(
      analysisType = "average",
      collection = collection,
      targetProperty = Some(targetProperty),
      filters = filters,
      timeframe = timeframe,
      timezone = timezone,
      groupBy = groupBy)

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
    groupBy: Option[String]= None): Future[Response] =

    doQuery(
      analysisType = "count",
      collection = collection,
      targetProperty = None,
      filters = filters,
      timeframe = timeframe,
      timezone = timezone,
      groupBy = groupBy)

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
    groupBy: Option[String]= None): Future[Response] =

    doQuery(
      analysisType = "count",
      collection = collection,
      targetProperty = Some(targetProperty),
      filters = filters,
      timeframe = timeframe,
      timezone = timezone,
      groupBy = groupBy)

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
    groupBy: Option[String]= None): Future[Response] =

    doQuery(
      analysisType = "maximum",
      collection = collection,
      targetProperty = Some(targetProperty),
      filters = filters,
      timeframe = timeframe,
      timezone = timezone,
      groupBy = groupBy)

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
    groupBy: Option[String]= None): Future[Response] =

    doQuery(
      analysisType = "minimum",
      collection = collection,
      targetProperty = Some(targetProperty),
      filters = filters,
      timeframe = timeframe,
      timezone = timezone,
      groupBy = groupBy)

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
    groupBy: Option[String]= None): Future[Response] =

    doQuery(
      analysisType = "select_unique",
      collection = collection,
      targetProperty = Some(targetProperty),
      filters = filters,
      timeframe = timeframe,
      timezone = timezone,
      groupBy = groupBy)

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
    groupBy: Option[String]= None): Future[Response] =

    doQuery(
      analysisType = "sum",
      collection = collection,
      targetProperty = Some(targetProperty),
      filters = filters,
      timeframe = timeframe,
      timezone = timezone,
      groupBy = groupBy)

  /**
   * Deletes the entire event collection. This is irreversible and will only work for collections under 10k events. See [[https://keen.io/docs/api/reference/#event-collection-resource Event Collection Resource]].
   *
   * @param collection The name of the collection.
   */
  def deleteCollection(collection: String): Future[Response] = {
    val path = Seq(version, "projects", settings.projectId, "events", collection).mkString("/")
    httpAdapter.delete(path = path, authKey = settings.masterKey)
  }

  /**
   * Removes a property and deletes all values stored with that property name. See [[https://keen.io/docs/api/reference/#property-resource Property Resource]].
   */
  def deleteProperty(collection: String, name: String): Future[Response] = {
    val path = Seq(version, "projects", settings.projectId, "events", collection, "properties", name).mkString("/")
    httpAdapter.delete(path = path, authKey = settings.masterKey)
  }

  /**
   * Returns schema information for all the event collections in this project. See [[https://keen.io/docs/api/reference/#event-resource Event Resource]].
   */
  def getEvents: Future[Response] = {
    val path = Seq(version, "projects", settings.projectId, "events").mkString("/")
    httpAdapter.get(path = path, authKey = settings.masterKey)
  }

  /**
   * Returns available schema information for this event collection, including properties and their type. It also returns links to sub-resources. See [[https://keen.io/docs/api/reference/#event-collection-resource Event Collection Resource]].
   *
   * @param collection The name of the collection.
   */
  def getCollection(collection: String): Future[Response] = {
    val path = Seq(version, "projects", settings.projectId, "events", collection).mkString("/")
    httpAdapter.get(path = path, authKey = settings.masterKey)
  }

  /**
   * Returns the projects accessible to the API user, as well as links to project sub-resources for
   * discovery. See [[https://keen.io/docs/api/reference/#projects-resource Projects Resource]].
   */
  def getProjects: Future[Response] = {
    val path = Seq(version, "projects").mkString("/")
    httpAdapter.get(path = path, authKey = settings.masterKey)
  }

  /**
   * Returns detailed information about the specific project, as well as links to related resources.
   * See [[https://keen.io/docs/api/reference/#project-row-resource Project Row Resource]].
   */
  def getProject: Future[Response] = {
    val path = Seq(version, "projects", settings.projectId).mkString("/")
    httpAdapter.get(path = path, authKey = settings.masterKey)
  }

  /**
   * Returns the property name, type, and a link to sub-resources. See [[https://keen.io/docs/api/reference/#property-resource Property Resource]].
   */
  def getProperty(collection: String, name: String): Future[Response] = {
    val path = Seq(version, "projects", settings.projectId, "events", collection, "properties", name).mkString("/")
    httpAdapter.get(path = path, authKey = settings.masterKey)
  }

  /**
   * Returns the list of available queries and links to them. See [[https://keen.io/docs/api/reference/#queries-resource Queries Resource]].
   */
  def getQueries: Future[Response] = {
    val path = Seq(version, "projects", settings.projectId, "queries").mkString("/")
    httpAdapter.get(path = path, authKey = settings.masterKey)
  }

  private def doQuery(
    analysisType: String,
    collection: String,
    targetProperty: Option[String],
    filters: Option[String] = None,
    timeframe: Option[String] = None,
    timezone: Option[String] = None,
    groupBy: Option[String]= None): Future[Response] = {

    val path = Seq(version, "projects", settings.projectId, "queries", analysisType).mkString("/")

    val params = Map(
      "event_collection" -> Some(collection),
      "target_property" -> targetProperty,
      "filters" -> filters,
      "timeframe" -> timeframe,
      "timezone" -> timezone,
      "group_by" -> groupBy
    )

    httpAdapter.get(path = path, authKey = settings.readKey, params = params)
  }


  /**
   * Disconnects any remaining connections. Both idle and active. If you are accessing
   * Keen through a proxy that keeps connections alive this is useful.
   */
  def shutdown {
    httpAdapter.shutdown
  }
}

