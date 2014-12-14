(ns eulalie.TestableAWS4Signer
  (:gen-class :extends com.amazonaws.auth.AWS4Signer
              :exposes {overriddenDate {:get getOverriddenDate
                                        :set setOverriddenDate}}))

