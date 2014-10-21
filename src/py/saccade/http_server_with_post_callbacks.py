import time
import BaseHTTPServer
import json
import traceback

def HTTPServerWithPostCallbacks(path_handlers, port_number):

    # Use a closure on path_handlers
    class MapPostsToCallbacksHandler(BaseHTTPServer.BaseHTTPRequestHandler):

        def do_GET(self):
            self.send_response(200)
            self.send_header("Content-type", "text/html")
            self.end_headers()
            self.wfile.write("This server handles HTTP posts. This is a GET request for path: %s" % self.path)
        def do_POST(self):
            content_len = int(self.headers.getheader('content-length'))
            post_body = self.rfile.read(content_len)
            request_path = self.path.lower()
            # print time.asctime(), "Received POST: %s %s" % (request_path, post_body)

            if not post_body.strip():
                message = None
            else:
                message = json.loads(post_body)

            request_path = self.path.lower()
            if request_path in path_handlers:
                try:
                    response_body = path_handlers[request_path](message)
                    response_code = 200
                except Exception as e:
                    print "Caught:", e
                    print traceback.format_exc()
                    response_body = e
                    response_code = 400
            else:
                response_body = ""
                response_code = 404

            # print time.asctime(), "Response: %s" % response_body
            self.send_response(response_code)
            self.send_header("Content-type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*");
            self.send_header("Access-Control-Expose-Headers", "Access-Control-Allow-Origin");
            self.send_header("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept");
            self.end_headers()
            self.wfile.write(response_body)

    return BaseHTTPServer.HTTPServer(server_address=("", port_number), RequestHandlerClass=MapPostsToCallbacksHandler)

def become_a_server(path_handlers):
    PORT_NUMBER = 8000
    httpd = HTTPServerWithPostCallbacks(path_handlers, PORT_NUMBER)
    print time.asctime(), "Server started (Port %s)" % PORT_NUMBER
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    httpd.server_close()
    print time.asctime(), "Server stopped (Port %s)" % PORT_NUMBER
