/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.graph.generator;

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.functions.FunctionAnnotation.ForwardedFields;
import org.apache.flink.graph.Edge;
import org.apache.flink.graph.Graph;
import org.apache.flink.graph.Vertex;
import org.apache.flink.types.LongValue;
import org.apache.flink.types.NullValue;
import org.apache.flink.util.Collector;
import org.apache.flink.util.LongValueSequenceIterator;

/*
 * @see <a href="http://mathworld.wolfram.com/CompleteGraph.html">Complete Graph at Wolfram MathWorld</a>
 */
public class CompleteGraph
extends AbstractGraphGenerator<LongValue, NullValue, NullValue> {

	// Required to create the DataSource
	private final ExecutionEnvironment env;

	// Required configuration
	private long vertexCount;

	/**
	 * An undirected {@link Graph} connecting every distinct pair of vertices.
	 *
	 * @param env the Flink execution environment
	 * @param vertexCount number of vertices
	 */
	public CompleteGraph(ExecutionEnvironment env, long vertexCount) {
		if (vertexCount <= 0) {
			throw new IllegalArgumentException("Vertex count must be greater than zero");
		}

		this.env = env;
		this.vertexCount = vertexCount;
	}

	@Override
	public Graph<LongValue,NullValue,NullValue> generate() {
		// Vertices
		DataSet<Vertex<LongValue,NullValue>> vertices = GraphGeneratorUtils.vertexSequence(env, parallelism, vertexCount);

		// Edges
		LongValueSequenceIterator iterator = new LongValueSequenceIterator(0, this.vertexCount - 1);

		DataSet<Edge<LongValue,NullValue>> edges = env
			.fromParallelCollection(iterator, LongValue.class)
				.setParallelism(parallelism)
				.name("Edge iterators")
			.flatMap(new LinkVertexToAll(vertexCount))
				.setParallelism(parallelism)
				.name("Complete graph edges");

		// Graph
		return Graph.fromDataSet(vertices, edges, env);
	}

	@ForwardedFields("*->f0")
	public class LinkVertexToAll
	implements FlatMapFunction<LongValue, Edge<LongValue,NullValue>> {

		private final long vertexCount;

		private LongValue target = new LongValue();

		private Edge<LongValue,NullValue> edge = new Edge<>(null, target, NullValue.getInstance());

		public LinkVertexToAll(long vertex_count) {
			this.vertexCount = vertex_count;
		}

		@Override
		public void flatMap(LongValue source, Collector<Edge<LongValue,NullValue>> out)
				throws Exception {
			edge.f0 = source;

			long s = source.getValue();
			long t = (s + 1) % vertexCount;

			while (s != t) {
				target.setValue(t);
				out.collect(edge);

				if (++t == vertexCount) {
					t = 0;
				}
			}
		}
	}
}
