package no.mesan.hipchatparse.system

/** Cannot continue :( */
case class Breakdown(message: String)

/** Status update. */
case class TaskDone(name: String)

/** Configure a value */
case class ConfigValue[A](name: String, value: A)