/**
 * Copyright 2016 Twitter. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.graphjet.demo;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.twitter.graphjet.bipartite.MultiSegmentPowerLawBipartiteGraph;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import org.eclipse.jetty.http.HttpStatus;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Servlet of {@link TwitterStreamReader} that returns the top <i>k</i> users in terms of degree in the user-tweet
 * bipartite graph.
 */
public class TopUsersServlet extends HttpServlet {
  private static final Joiner JOINER = Joiner.on(",\n");
  private final MultiSegmentPowerLawBipartiteGraph bigraph;
  private final Long2ObjectMap<String> users;

  public TopUsersServlet(MultiSegmentPowerLawBipartiteGraph bigraph, Long2ObjectOpenHashMap<String> users) {
    this.bigraph = bigraph;
    this.users = users;
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    int maxNumResults = 10;
    String requestMaxNumResults = request.getParameter("k");
    if (requestMaxNumResults != null) {
      try {
        maxNumResults = Integer.parseInt(requestMaxNumResults);
      } catch (NumberFormatException e) {
        // Just eat it, don't need to worry.
      }
    }

    PriorityQueue<NodeValueEntry> queue = new PriorityQueue<>(maxNumResults);
    LongIterator iter = users.keySet().iterator();
    while (iter.hasNext()) {
      long user = iter.nextLong();
      int cnt = bigraph.getLeftNodeDegree(user);
      if (cnt == 1) continue;

      if (queue.size() < maxNumResults) {
        queue.add(new NodeValueEntry(user, cnt));
      } else {
        NodeValueEntry peek = queue.peek();
        // Break ties by preferring higher userid (i.e., more recent user)
        if (cnt > peek.getValue() || (cnt == peek.getValue() && user > peek.getNode())) {
          queue.poll();
          queue.add(new NodeValueEntry(user, cnt));
        }
      }
    }

    if (queue.size() == 0) {
      response.getWriter().println("[]\n");
      return;
    }

    NodeValueEntry e;
    List<String> entries = new ArrayList<>(queue.size());
    while ((e = queue.poll()) != null) {
      // Note that we explicitly use id_str and treat the tweet id as a String. See:
      // https://dev.twitter.com/overview/api/twitter-ids-json-and-snowflake
      entries.add(String.format("{\"id_str\": \"%d\", \"cnt\": %d}", e.getNode(), (int) e.getValue()));
    }

    response.setStatus(HttpStatus.OK_200);
    response.getWriter().println("[\n" + JOINER.join(Lists.reverse(entries)) + "\n]");
  }
}
