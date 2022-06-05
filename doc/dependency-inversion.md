
# Dependency Inversion

Consider a typical Clojure web application (example borrowed from James Reeves'
[Enter Integrant][enter-integrant] talk):

```clojure
(def database
  (db/connect "jdbc:sqlite:"))

(defn handler [request]
  (resp/response (query-status database)))

(defn -main []
  (jetty/run-jetty handler {:port 8080}))
```

Code like this is difficult to test and reason about.  The `database` is
_global state_, and the fact that the `handler` requires the `database` is
_implicit_ and _opaque_.  In order to test `handler`, you will probably need
to mock the database with something like `with-redefs`.  As the application
grows, keeping track of these implicit dependencies will prove a challenge.

Applying _dependency inversion_, we rewrite this code like this:

```clojure
(defn read-config []
  (edn/read-string (slurp "config.edn")))

(defn database [uri]
  (db/connect uri))

(defn handler [database]
  (fn [request]
    (resp/response (query-status database))))

(defn start-server [port]
  (jetty/run-jetty handler {:port port}))

(defn -main []
  (let [config  (load-config)
        db      (database (:db-uri config))
        handler (handler database)]
    (start-server handler (:port config))))
```

All vars are now functions, taking their dependencies as arguments.  They
are less coupled, and easier to test: Just pass in test doubles.

However, the code got more complex, and manually keeping track of
initialization order might get tiresome as the application grows.

## Metadata configuration

With Init, the example above can be written like this:

```clojure
(defn read-config
  {:init/name ::config}
  []
  (edn/read-string (slurp "config.edn")))

(defn database
  {:init/inject [[:get ::config :db-uri]]}
  [uri]
  (db/connect uri))

(defn handler
  {:init/inject [:into-first {:db ::database}]}
  [{:keys [db] :as request}]
  (resp/response (query-status db)))

(defn start-server
  {:init/inject [::handler [:get ::config :port]]}
  [handler port]
  (jetty/run-jetty handler {:port port}))

(defn -main []
  (-> (discovery/from-namespaces [*ns*])
      (init/start)))
```

We use metadata to _describe_ a function's dependencies, then let Init figure
out how to wire components together.

[enter-integrant]: https://skillsmatter.com/skillscasts/9820-enter-integrant
