# init
Dependency injection framework for Clojure

## Rationale

Without dependency injection:

```clojure
(def database
  (db/connect "jdbc:sqlite:"))

(defn handler [request]
 (resp/response (query-status database)))

(defn -main []
  (jetty/run-jetty handler {:port 8080}))
```

Use metadata to configure injection:

```clojure
(defn config []
  (aero/read-config "config.edn"))

(defn database
  {:init/inject [[:get ::config :database :uri]]}
  [uri]
  (db/connect uri))

(defn handler
  {:init/inject [:into-first ::database]}
  [request]
  (resp/response (query-status (::database request))))

(defn jetty
  {:init/inject [::handler {:port [:get ::config :port]}]}
  [handler {:keys [port] :or {port 8080}}]
  (jetty/run-jetty handler {:port port}))
```
