package io.akka.focalboard.api;

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.http.HttpResponses;

/**
 * Serves focalboard's own web interface, vendored under {@code webapp/} and built into
 * {@code src/main/resources/static-resources/}.
 *
 * <p>RENDERING.md R3 — this is the original's interface, not a smaller one standing in for it.
 * What the port changed is its data layer; {@code focalboard-port/docs/webapp-diff.md} names
 * every file.
 */
@HttpEndpoint
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class UiEndpoint {

  @Get("/")
  public HttpResponse index() {
    return HttpResponses.staticResource("index.html");
  }

  /**
   * Routing is the application's, not this endpoint's: a path with no file behind it is one of
   * focalboard's own routes and gets the shell, which is what lets
   * {@code /team/0/<board>/<view>} open directly rather than only by clicking.
   */
  @Get("/**")
  public HttpResponse asset(HttpRequest request) {
    String path = request.getUri().path();
    // /akka/ is the runtime's own namespace, not the application's. A catch-all that answers
    // there tells the runtime's health check that a path it expects to be absent exists, and
    // the service is then reported as never having started.
    if (path.startsWith("/akka/") || path.startsWith("/api/")) {
      return HttpResponses.notFound();
    }
    if (looksLikeAFile(path)) {
      return HttpResponses.staticResource(request, "/");
    }
    return HttpResponses.staticResource("index.html");
  }

  private static boolean looksLikeAFile(String path) {
    int lastSlash = path.lastIndexOf('/');
    return path.indexOf('.', lastSlash) > -1;
  }
}
