(ns ^:no-doc eulalie.service.dynamo.impl.key-types)

(def ^:private attr-keys
  #{:unprocessed-keys :attribute-updates :key-conditions
    :query-filter :scan-filter :expected :request-items
    :attribute-definitions :local-secondary-indexes
    :global-secondary-indexes :key-schema  :global-secondary-index-updates
    :attr})

(def ^:private keyword-keys
  #{:key-type :projection-type :select :table-status :index-status
    :return-consumed-capacity :return-values :return-item-collection-metrics
    :comparison-operator :conditional-operator :action :stream-view-type
    :attribute-name :attribute-type :table-name :index-name :non-key-attributes
    :last-evaluated-table-name :table-names :attributes-to-get
    :exclusive-start-table-name})

(def request-key-types
  (merge
   {:projection-expression  :list
    :stream-specification   :nest
    :put-request            :nest
    :delete-request         :nest
    :projection             :nest
    :provisioned-throughput :nest
    :consumed-capacity      :nest
    :update                 :nest
    :create                 :nest
    :delete                 :nest}
   (zipmap keyword-keys (repeat :keys))
   (zipmap attr-keys    (repeat :attr))))

(def response-key-types
  (merge
   request-key-types

   {:table             :nest
    :table-description :nest
    :expression-attribute-names :keys}

   (zipmap #{:local-secondary-indexes
             :global-secondary-indexes
             :item-collection-metrics
             :unprocessed-items
             :unprocessed-keys
             :attribute-updates} (repeat :attr))))
