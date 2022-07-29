import java.util.Date;

import org.softwareheritage.graph.SwhBidirectionalGraph;
import org.softwareheritage.graph.SwhType;
import org.softwareheritage.graph.labels.DirEntry;

import it.unimi.dsi.big.webgraph.LazyLongIterator;
import it.unimi.dsi.big.webgraph.labelling.ArcLabelledNodeIterator.LabelledArcIterator;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongStack;

public class Experiment {
    private static LongOpenHashSet discoveredOrigins = new LongOpenHashSet();
    private static LongOpenHashSet selectedProjects = new LongOpenHashSet();

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

    private static String getCommitMessage(SwhBidirectionalGraph graph, long node) {
        assert graph.getNodeType(node) == SwhType.REV;
        byte[] message = graph.getMessage(node);
        if (message != null) {
            return new String(graph.getMessage(node));
        }
        else {
            return new String("");
        }
    }

    // Code from https://docs.softwareheritage.org/devel/swh-graph/java-api.html#example-find-all-the-shared-commit-forks-of-a-given-origin
    // 
    // Adapted to mark fork origins as discovered in discoveredOrigins and to
    // return the origin that has the longest chain of revisions to any root
    // revision.
    public static long findLongestFork(SwhBidirectionalGraph graph, long srcOrigin) {
        LongStack forwardStack = new LongArrayList();
        LongOpenHashSet forwardVisited = new LongOpenHashSet();
        LongArrayList backwardStack = new LongArrayList();
        LongOpenHashSet backwardVisited = new LongOpenHashSet();

        // First traversal (forward graph): find all the root revisions of the
        // origin
        forwardStack.push(srcOrigin);
        forwardVisited.add(srcOrigin);
        while (!forwardStack.isEmpty()) {
            long curr = forwardStack.popLong();
            LazyLongIterator it = graph.successors(curr);
            boolean isRootRevision = true;
            for (long succ; (succ = it.nextLong()) != -1;) {
                SwhType nt = graph.getNodeType(succ);
                if (nt != SwhType.DIR && nt != SwhType.CNT) {
                    isRootRevision = false;
                    if (!forwardVisited.contains(succ)) {
                        forwardStack.push(succ);
                        forwardVisited.add(succ);
                    }
                }
            }
            if (graph.getNodeType(curr) == SwhType.REV && isRootRevision) {
                // Found a root revision, add it to the second stack
                backwardStack.push(curr);
                backwardVisited.add(curr);
            }
            else {
            }
        }

        if (backwardStack.isEmpty()) {
            // TODO: no root revision found, something went wrong, ignore this
            // origin for now
            // j'ai l'impression qu'il ne remonte pas les merge commit
            return -1;
        }

        // Second traversal (backward graph): find all the origins containing
        // any of these root revisions and print them. Also remember the origin
        // that is the farthest from any root revision.
        long farthestOrigin = -1;
        long largestDistance = -1;

        for (long rootRevision: backwardStack) {
            // BFS to find origins while making sure the last one we see is the
            // farthest from `rootRevision`
            LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
            queue.enqueue(rootRevision);
            queue.enqueue(-1); // level marker
            long currentDistance = 0;
            while (!queue.isEmpty()) {
                long curr = queue.dequeueLong();
                if (curr == -1) {
                    // level marker
                    if (!queue.isEmpty()) {
                        queue.enqueue(-1);
                    }
                    currentDistance++;
                    continue;
                }

                LazyLongIterator it = graph.predecessors(curr);
                for (long succ; (succ = it.nextLong()) != -1;) {
                    if (!backwardVisited.contains(succ)) {
                        queue.enqueue(succ);
                        backwardVisited.add(succ);
                        if (graph.getNodeType(succ) == SwhType.ORI) {
                            // Found a fork origin
                            discoveredOrigins.add(succ);
                            if (currentDistance > largestDistance) {
                                if (farthestOrigin != -1) {
                                    System.out.format(
                                        "> (%d > %d) %s is farther than %s\n",
                                        currentDistance,
                                        largestDistance,
                                        getOriginUrl(graph, succ),
                                        getOriginUrl(graph, farthestOrigin)
                                    );
                                }
                                farthestOrigin = succ;
                                largestDistance = currentDistance;
                            }
                        }
                    }
                }
            }
        }

        assert farthestOrigin != -1;
        return farthestOrigin;
    }

    private static String getOriginUrl(SwhBidirectionalGraph graph, long origin) {
        assert graph.getNodeType(origin) == SwhType.ORI;
        return new String(graph.getMessage(origin));
    }


    // If the given origin wasn't already discovered, find all the origins that
    // have some revisions in common with this one (forks) and select one
    // representative among them for later analysis.
    private static void discoverNewOrigin(SwhBidirectionalGraph graph, long origin) {
        if (discoveredOrigins.contains(origin)) {
            return;
        }
        discoveredOrigins.add(origin);
        long longestFork = findLongestFork(graph, origin);
        if (longestFork == -1) {
            // buggy origin (see todo in findLongestFork)
            return;
        }
        if (longestFork != origin) {
            System.out.format(
                ">>>>> %s (%d) longest fork is %s (%d)\n",
                getOriginUrl(graph, origin),
                origin,
                getOriginUrl(graph, longestFork),
                longestFork
            );
        }
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
        System.out.println(getOriginUrl(graph, origin));

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

        // Select projects
        System.out.format("Starting traversal of %d nodes\n", graph.numNodes());
        for (long node = 0; node < graph.numNodes(); node++) {
            if (graph.getNodeType(node) == SwhType.ORI) {
                // TODO: find other origins that are forks using
                // https://docs.softwareheritage.org/devel/swh-graph/java-api.html#example-find-all-the-shared-commit-forks-of-a-given-origin
                // and select only a representative origin
                discoverNewOrigin(graph, node);
            }
        }

        for (long project : selectedProjects) {
            System.out.format("Analyzing %s\n", graph.getSWHID(project));
            analyzeProject(graph, project);
            System.out.println("----------------------- next project ----------------");
        }
        System.out.format("Analyzed %d projects\n", selectedProjects.size());
    }
}
