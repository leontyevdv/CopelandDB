1. Mini analytics DB: all of the above plus a query layer (Calcite or hand-rolled) with a couple of operators (filter, project, aggregate) over the columnar store.
2. Build on top of Lucene: implement the columnar store as a custom Lucene Codec / DocValuesFormat / PostingsFormat. Study Lucene internals deeply by participating in its segment/codec machinery.
