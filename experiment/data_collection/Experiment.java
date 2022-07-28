import java.util.ArrayList;
import java.util.Date;

import org.softwareheritage.graph.SWHID;
import org.softwareheritage.graph.SwhBidirectionalGraph;
import org.softwareheritage.graph.SwhType;
import org.softwareheritage.graph.labels.DirEntry;

import it.unimi.dsi.big.webgraph.LazyLongIterator;
import it.unimi.dsi.big.webgraph.labelling.ArcLabelledNodeIterator.LabelledArcIterator;
import it.unimi.dsi.fastutil.longs.LongBigArrays;

public class Experiment {
    // 
    // Code from https://forge.softwareheritage.org/D7936 line 60
    private static boolean isBaseRevision(SwhBidirectionalGraph graph, long node) {
        // Is it a revision
        if (graph.getNodeType(node) != SwhType.REV)
            return false;

        // Does it have any *revision* successor
        final LazyLongIterator iterator = graph.successors(node);
        long succ;
        while ((succ = iterator.nextLong()) != -1) {
            if (graph.getNodeType(succ) == SwhType.REV)
                return false;
        }

        return true;
    }

    // Code from https://docs.softwareheritage.org/devel/swh-graph/java-api.html
    private static long findDirectoryOfRevision(SwhBidirectionalGraph graph, long src) {
        assert graph.getNodeType(src) == SwhType.REV;

        // return the first successor that is a DIR
        LazyLongIterator it = graph.successors(src);
        for (long dst; (dst = it.nextLong()) != -1;) {
            if (graph.getNodeType(dst) == SwhType.DIR) {
                return dst;
            }
        }

        return -1;
    }

    // Code from https://docs.softwareheritage.org/devel/swh-graph/java-api.html
    private static void printDirEntries(SwhBidirectionalGraph graph, long dirNode) {
        assert graph.getNodeType(dirNode) == SwhType.DIR;

        LabelledArcIterator it = graph.labelledSuccessors(dirNode);
        for (long dst; (dst = it.nextLong()) >= 0;) {
            DirEntry[] labels = (DirEntry[]) it.label().get();
            for (DirEntry label : labels) {
                System.out.format(
                    "%s %s %d\n",
                    graph.getSWHID(dst),
                    new String(graph.getLabelName(label.filenameId)),
                    label.permission
                );
            }
        }
    }

    // TODO
    private static void analyzeProject(SwhBidirectionalGraph graph, long baseRevId) {
        assert graph.getNodeType(baseRevId) == SwhType.REV;

        try {
            long timestamp = graph.getCommitterTimestamp(baseRevId);
            Date initialCommitDate = new Date(timestamp * 1000);
            System.out.println(initialCommitDate);
        }
        catch (java.lang.NullPointerException e) {
            return;
        }

        // Print the commit message
        String message = new String(graph.getMessage(baseRevId));
        System.out.println(message);

        // List all files in the root directory of the base revision
        long targetDirId = findDirectoryOfRevision(graph, baseRevId);
        if (targetDirId != -1) {
            printDirEntries(graph, targetDirId);
        }
    }

    public static void main(String[] args) {
        String graphPath = args[1];
        SwhBidirectionalGraph graph;
        System.out.print("Loading the graph");
        try {
            if (args[0].equals("mapped")) {
                System.out.println(" (mapped)");
                graph = SwhBidirectionalGraph.loadLabelledMapped(graphPath);
            }
            else {
                System.out.println(" (in memory)");
                graph = SwhBidirectionalGraph.loadLabelled(graphPath);
            }
            graph.loadMessages();
            graph.loadCommitterTimestamps();
            graph.loadPersonIds();
            graph.loadLabelNames();
        } catch(Exception e) {
            System.out.println(e.getMessage());
            return;
        }

        long projectCount = 0;
        System.out.format("Starting traversal of %d nodes\n", graph.numNodes());
        for (long node = 0; node < graph.numNodes(); node++) {
            if (isBaseRevision(graph, node)) {
                analyzeProject(graph, node);
                projectCount++;
            }
        }

        System.out.format("Analyzed %d projects\n", projectCount);
    }
}
