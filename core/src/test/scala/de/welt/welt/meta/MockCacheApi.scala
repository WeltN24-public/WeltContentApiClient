package de.welt.welt.meta

import play.api.cache.CacheApi

import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

object MockCacheApi extends CacheApi {
  override def set(key: String, value: Any, expiration: Duration): Unit = ???

  override def get[T: ClassTag](key: String): Option[T] = ???

  override def getOrElse[A: ClassTag](key: String, expiration: Duration = Duration.Inf)(orElse: ⇒ A): A = {
    orElse
  }

  override def remove(key: String): Unit = {}
}
