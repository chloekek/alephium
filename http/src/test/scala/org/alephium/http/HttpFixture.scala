// Copyright 2018 The Alephium Authors
// This file is part of the alephium project.
//
// The library is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// The library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with the library. If not, see <http://www.gnu.org/licenses/>.

package org.alephium.http

import org.scalatest.Assertion
import sttp.client3._
import sttp.model.{Method, Uri}

import org.alephium.json.Json._

object HttpFixture {
  implicit class RichResponse[T](val response: Response[T]) extends AnyVal {
    def check(f: Response[T] => Assertion): Assertion = {
      f(response)
    }
  }

  implicit class RichEitherResponse(val response: Response[Either[String,String]]) extends AnyVal {
     def as[T:Reader]: T = {
       val body = response.body match {
         case Right(r)=>r
         case Left(l)=>l
       }
       read[T](body)
     }
  }
}

trait HttpFixture {

  type HttpRequest = RequestT[Identity,Either[String,String],Any]

  val backend = HttpURLConnectionBackend()

  def httpRequest[T,R](
      method: Method,
      endpoint: String,
      maybeBody: Option[String] = None
  ): Int =>  HttpRequest = { port =>

    val request = basicRequest
      .method(method,parseUri(endpoint).port(port))

    val requestWithBody = maybeBody match {
      case Some(body) => request.body(body).contentType("application/json")
      case None         => request
    }

    requestWithBody
  }

  def httpGet(endpoint: String, maybeBody: Option[String] = None) =
    httpRequest(Method.GET, endpoint, maybeBody)
  def httpPost(endpoint: String, maybeBody: Option[String] = None) =
    httpRequest(Method.POST, endpoint, maybeBody)
  def httpPut(endpoint: String, maybeBody: Option[String] = None) =
    httpRequest(Method.PUT, endpoint, maybeBody)

// scalastyle:off no.equal
  def parsePath(str: String): (Seq[String], Map[String, String]) = {
    if (str.head != '/') {
      throw new Throwable("Path doesn't start with '/'")
    } else {
      val path = str.split('/')
      if (path.head == "/") {
        //root path
        (Seq.empty, Map.empty)
      } else {
        val base = Seq.from(path.tail)
        if (base.last.contains('?')) {
          val res       = base.last.split('?')
          val rawParams = res.tail.head
          val params = rawParams
            .split('&')
            .map { params =>
              val splited = params.split('=')
              (splited.head, splited.tail.head)
            }
            .toMap

          (base.init.appended(res.head), params)
        } else {
          (base, Map.empty)
        }
      }
    }
  }
// scalastyle:on no.equal

  def parseUri(endpoint: String): Uri = {
    val (path, params) = parsePath(endpoint)
    val base           = Uri("127.0.0.1").withPath(path)
    if (params.isEmpty) {
      base
    } else {
      base.withParams(params)
    }
  }
}

trait HttpRouteFixture extends HttpFixture {
  def port: Int

  def Post(endpoint: String, maybeBody: Option[String]): Response[Either[String, String]] = {
    httpPost(endpoint,maybeBody)(port).send(backend)
  }

  def Post(endpoint: String): Response[Either[String, String]]               =
    Post(endpoint, None)

  def Post(endpoint: String, body: String): Response[Either[String, String]] =
    Post(endpoint, Some(body))

  def Put(endpoint: String, body: String) = {
    httpPut(endpoint, Some(body))(port).send(backend)
  }

  def Delete(endpoint: String, body: String) = {
    httpRequest(Method.DELETE, endpoint, Some(body))(port).send(backend)
  }

  def Get(endpoint: String, otherPort: Int = port): Response[Either[String, String]] = {
    httpGet(endpoint)(otherPort).send(backend)
  }

  def Get(endpoint: String, body: String): Response[Either[String, String]] = {
    httpGet(endpoint, Some(body))(port).send(backend)
  }
}
