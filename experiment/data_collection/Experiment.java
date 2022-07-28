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

    private static Date getCommitDate(SwhBidirectionalGraph graph, long src) {
            long timestamp = graph.getCommitterTimestamp(src);
            return new Date(timestamp * 1000);
    }

    private static long printSnapshots(SwhBidirectionalGraph graph, long origin) {
        assert graph.getNodeType(origin) == SwhType.ORI;

        LazyLongIterator snps = graph.successors(origin);
        for (long snp; (snp = snps.nextLong()) != -1;) {
            if (graph.getNodeType(snp) == SwhType.SNP) {
                System.out.print("SNP: ");
                // show the successors
                LabelledArcIterator revs = graph.labelledSuccessors(snp);
                for (long rev; (rev = revs.nextLong()) != -1;) {
                    System.out.print(graph.getNodeType(rev) + "(");
                    // print labels
                    DirEntry[] labels = (DirEntry[]) revs.label().get();
                    for (DirEntry label: labels) {
                        String branch_name = new String(graph.getLabelName(label.filenameId));
                        // TODO: maybe keep select only projects where we can
                        // find a branch named "main" or "master" or "dev" ?
                        System.out.print(branch_name);
                    }
                    System.out.print(") ");
                }
                System.out.println("");
            }
            else {
                System.out.println("skiped a " + graph.getNodeType(snp));
            }
        }

        return -1;
    }

    private static void analyzeProject(SwhBidirectionalGraph graph, long origin) {
        assert graph.getNodeType(origin) == SwhType.ORI;

        try {
            System.out.print(getCommitDate(graph, origin)+ ": ");
        }
        catch (java.lang.NullPointerException e) {}

        // Print the origin url
        String url = new String(graph.getMessage(origin));
        System.out.println(url);

        printSnapshots(graph, origin);
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
            graph.loadTagNames();
        } catch(Exception e) {
            System.out.println(e.getMessage());
            return;
        }

        long projectCount = 0;
        System.out.format("Starting traversal of %d nodes\n", graph.numNodes());
        for (long node = 0; node < graph.numNodes(); node++) {
            if (graph.getNodeType(node) == SwhType.ORI) {
                // TODO: find other origins that are forks using
                // https://docs.softwareheritage.org/devel/swh-graph/java-api.html#example-find-all-the-shared-commit-forks-of-a-given-origin
                // and select only a representative origin
                System.out.format("Analyzing %s\n", graph.getSWHID(node));
                analyzeProject(graph, node);
                System.out.println("----------------------- next project ----------------");
                projectCount++;
            }
        }

        System.out.format("Analyzed %d projects\n", projectCount);
    }
}
