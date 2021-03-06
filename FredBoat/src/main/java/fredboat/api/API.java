/*
 * MIT License
 *
 * Copyright (c) 2017 Frederik Ar. Mikkelsen
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
 *
 */

package fredboat.api;

import fredboat.audio.player.PlayerRegistry;
import fredboat.feature.metrics.Metrics;
import fredboat.main.BotController;
import fredboat.main.BotMetrics;
import net.dv8tion.jda.core.JDA;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;


public class API {

    private static final Logger log = LoggerFactory.getLogger(API.class);

    private static final int PORT = 1356;

    private API() {}

    public static void start() {
        if (!BotController.INS.getAppConfig().isRestServerEnabled()) {
            log.warn("Rest server is not enabled. Skipping Spark ignition!");
            return;
        }

        log.info("Igniting Spark API on port: " + PORT);

        Spark.port(PORT);

        Spark.before((request, response) -> {
            log.info(request.requestMethod() + " " + request.pathInfo());
            response.header("Access-Control-Allow-Origin", "*");
            response.type("application/json");
        });

        Spark.get("/stats", (req, res) -> {
            Metrics.apiServed.labels("/stats").inc();
            res.type("application/json");

            JSONObject root = new JSONObject();
            JSONArray a = new JSONArray();

            for (JDA shard : BotController.INS.getShardManager().getShards()) {
                JSONObject fbStats = new JSONObject();
                fbStats.put("id", shard.getShardInfo().getShardId())
                        .put("guilds", shard.getGuildCache().size())
                        .put("users", shard.getUserCache().size())
                        .put("status", shard.getStatus());

                a.put(fbStats);
            }

            JSONObject g = new JSONObject();
            g.put("playingPlayers", PlayerRegistry.getPlayingPlayers().size())
                    .put("totalPlayers", PlayerRegistry.getRegistry().size())
                    .put("distribution", BotController.INS.getAppConfig().getDistribution())
                    .put("guilds", BotMetrics.getTotalGuildsCount())
                    .put("users", BotMetrics.getTotalUniqueUsersCount());

            root.put("shards", a);
            root.put("global", g);

            return root;
        });

        /* Exception handling */
        Spark.exception(Exception.class, (e, request, response) -> {
            log.error(request.requestMethod() + " " + request.pathInfo(), e);

            response.body(ExceptionUtils.getStackTrace(e));
            response.type("text/plain");
            response.status(500);
        });
    }

    public static void turnOnMetrics() {
        if (!BotController.INS.getAppConfig().isRestServerEnabled()) {
            log.warn("Rest server is not enabled. Skipping Spark ignition!");
            return;
        }

        Spark.get("/metrics", (req, resp) -> {
            Metrics.apiServed.labels("/metrics").inc();
            return Metrics.instance().metricsServlet.servletGet(req.raw(), resp.raw());
        });
    }

}
