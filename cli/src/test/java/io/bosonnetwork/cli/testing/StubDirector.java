/*
 * Copyright (c) 2023 -      bosonnetwork.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.bosonnetwork.cli.testing;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;

import io.bosonnetwork.Id;
import io.bosonnetwork.json.Json;

/**
 * A stand-in for a Director: answers each request with the reply set for its method and path, and
 * records the requests. Tokens are not checked; the Director's own tests cover that.
 */
public final class StubDirector implements AutoCloseable {
	/**
	 * A request the stub received.
	 *
	 * @param method the method
	 * @param path   the path
	 * @param query  the query string, or {@code null}
	 * @param body   the body
	 */
	public record Request(String method, String path, String query, String body) {
		/**
		 * Parses the body as a JSON object.
		 *
		 * @return the object
		 */
		public Map<String, Object> json() {
			return Json.parse(body);
		}
	}

	private record Reply(int status, String body) {
	}

	private final Vertx vertx;
	private final HttpServer server;
	private final Map<String, Reply> replies = new ConcurrentHashMap<>();
	private final List<Request> requests = new CopyOnWriteArrayList<>();

	private StubDirector(Vertx vertx) throws Exception {
		this.vertx = vertx;
		this.server = vertx.createHttpServer()
				.requestHandler(req -> req.body().onSuccess(body -> {
					requests.add(new Request(req.method().name(), req.path(), req.query(), body.toString(StandardCharsets.UTF_8)));
					Reply reply = replies.get(req.method().name() + " " + req.path());
					if (reply == null)
						req.response().setStatusCode(404).end("Not Found - no stub reply for " + req.method() + " " + req.path());
					else
						req.response().setStatusCode(reply.status()).putHeader("Content-Type", "application/json").end(reply.body());
				}))
				.listen(0, "127.0.0.1")
				.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
	}

	/**
	 * Starts a stub.
	 *
	 * @return the stub
	 * @throws Exception if it cannot listen
	 */
	public static StubDirector start() throws Exception {
		return new StubDirector(Vertx.vertx());
	}

	/**
	 * Sets the reply to a request.
	 *
	 * @param method the method
	 * @param path   the path, without the query string
	 * @param status the status
	 * @param body   the body
	 * @return this stub
	 */
	public StubDirector reply(String method, String path, int status, String body) {
		replies.put(method + " " + path, new Reply(status, body));
		return this;
	}

	/**
	 * Answers the node id lookups of both APIs.
	 *
	 * @param nodeId the node id
	 * @return this stub
	 */
	public StubDirector nodeId(Id nodeId) {
		String body = "{\"id\": \"" + nodeId.toBase58String() + "\"}";
		reply("GET", "/api/v1/client/id", 200, body);
		reply("GET", "/api/v1/admin/id", 200, body);
		return this;
	}

	/**
	 * Forgets the replies and the requests.
	 */
	public void reset() {
		replies.clear();
		requests.clear();
	}

	/**
	 * Returns the stub's URL.
	 *
	 * @return the URL
	 */
	public String url() {
		return "http://127.0.0.1:" + port();
	}

	/**
	 * Returns the port the stub listens on.
	 *
	 * @return the port
	 */
	public int port() {
		return server.actualPort();
	}

	/**
	 * Returns the requests received, in order.
	 *
	 * @return the requests
	 */
	public List<Request> requests() {
		return requests;
	}

	/**
	 * Returns the requests with a method and path.
	 *
	 * @param method the method
	 * @param path   the path
	 * @return the requests, in order
	 */
	public List<Request> requests(String method, String path) {
		return requests.stream().filter(r -> r.method().equals(method) && r.path().equals(path)).toList();
	}

	@Override
	public void close() throws Exception {
		vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
	}
}
