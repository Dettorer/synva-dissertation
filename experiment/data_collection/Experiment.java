import java.util.Date;
import java.util.HashMap;

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
    static class Contributor {
        long committerId;
        long firstContributionNode;
        Date firstContributionDate;
        long commitCount;
        boolean coreContributor = false;
    };

    // The set of ORI objects visited during the initial discovery
    private static LongOpenHashSet discoveredOrigins = new LongOpenHashSet();
    // The set of SNP objects selected as the entry points for project analysis
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

    private static long getOriginOfSnapshot(SwhBidirectionalGraph graph, long snapshot) {
        LazyLongIterator it = graph.predecessors(snapshot);
        for (long succ; (succ = it.nextLong()) != -1;) {
            if (graph.getNodeType(succ) == SwhType.ORI) {
                return succ;
            }
        }

        // No origin found for that snapshot, this should not happen
        return -1;
    }

    // Code from
    // https://docs.softwareheritage.org/devel/swh-graph/java-api.html#example-find-all-the-shared-commit-forks-of-a-given-origin
    // 
    // Adapted to mark fork origins as discovered in discoveredOrigins and to
    // return the snapshot that has the longest chain of revisions to any root
    // revision.
    //
    // Unfortunately this approach means that if multiple meaningfully different
    // projects share even only one commit somewhere (e.g. they all started as a
    // fork of a common small "template" project) in their history, we will
    // consider them forks of the same project and select only one of them for
    // analysis, ignoring all the others.
    public static long findLongestFork(SwhBidirectionalGraph graph, long srcOrigin) {
        LongStack forwardStack = new LongArrayList();
        LongOpenHashSet forwardVisited = new LongOpenHashSet();
        LongArrayList backwardStack = new LongArrayList();

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
            }
        }

        // Second traversal (backward graph): find all the origins containing
        // any of these root revisions and keep the farthest one.
        long farthestOrigin = -1;
        long largestDistance = -1;

        for (long rootRevision: backwardStack) {
            // BFS to find origins while making sure the last one we see is the
            // farthest from `rootRevision`
            LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
            LongOpenHashSet visited = new LongOpenHashSet();
            queue.enqueue(rootRevision);
            queue.enqueue(-1); // level marker
            long currentDistance = 0;
            while (!queue.isEmpty()) {
                long curr = queue.dequeueLong();
                if (curr == -1) {
                    // new level
                    if (!queue.isEmpty()) {
                        queue.enqueue(-1);
                    }
                    currentDistance++;
                    continue;
                }

                LazyLongIterator it = graph.predecessors(curr);
                for (long succ; (succ = it.nextLong()) != -1;) {
                    if (!visited.contains(succ)) {
                        queue.enqueue(succ);
                        visited.add(succ);
                        if (graph.getNodeType(succ) == SwhType.SNP) {
                            // Found a snapshot, mark its origin as discovered
                            long origin;
                            if ((origin = getOriginOfSnapshot(graph, succ)) != -1) {
                                discoveredOrigins.add(origin);
                            }
                            // and compare its distance to the farthest snapshot
                            // we found
                            if (currentDistance > largestDistance) {
                                farthestOrigin = succ;
                                largestDistance = currentDistance;
                            }
                        }
                    }
                }
            }
        }

        return farthestOrigin;
    }

    private static String getOriginUrl(SwhBidirectionalGraph graph, long origin) {
        if (origin == -1) {
            return "<no origin>";
        } else {
            assert graph.getNodeType(origin) == SwhType.ORI;
            return new String(graph.getMessage(origin));
        }
    }

    // If the given origin wasn't already discovered, find all the origins that
    // have some revisions in common with this one (forks) and select one
    // representative among them for later analysis.
    private static void discoverNewOrigin(SwhBidirectionalGraph graph, long origin) {
        if (discoveredOrigins.contains(origin)) {
            return;
        }
        discoveredOrigins.add(origin);

        long bestSnapshot = findLongestFork(graph, origin);
        if (bestSnapshot != -1) {
            // ignore buggy origins (softwareheritage sometimes have origin
            // objects that aren't actually archived, findLongestFork will
            // return -1 for such buggy origins)
            selectedProjects.add(bestSnapshot);
        }
    }

    private static Date getCommitDate(SwhBidirectionalGraph graph, long src) {
        assert graph.getNodeType(src) == SwhType.REV;
        long timestamp = graph.getCommitterTimestamp(src);
        return new Date(timestamp * 1000);
    }

    // experimentation with labelled arcs
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

    private static HashMap<String, Long> mainBranchScore = new HashMap<String, Long>() {{
        put("main", 100l);
        put("trunk", 100l);
        put("master", 90l);
        put("default", 80l);
        put("develop", 70l);
        put("dev", 70l);
        put("development", 70l);
    }};

    private static long findBestBranch(SwhBidirectionalGraph graph, long snapshot) {
        assert graph.getNodeType(snapshot) == SwhType.SNP;
        String bestBranchName = "";
        long bestBranchScore = -1;
        long bestBranch = -1;

        LabelledArcIterator it = graph.labelledSuccessors(snapshot);
        for (long succ; (succ = it.nextLong()) != -1;) {
            if (graph.getNodeType(succ) != SwhType.REV) {
                // not a branch
                continue;
            }

            DirEntry[] labels = (DirEntry[]) it.label().get();
            if (labels.length == 0) {
                // unnamed branch
                if (bestBranch == -1) {
                    // if no branch were found yet, use this one but keep the -1
                    // score so that it's replaced by any named branch we may
                    // find later
                    bestBranch = succ;
                    continue;
                }
            }
            String fullBranchName = new String(graph.getLabelName(labels[0].filenameId));
            String[] branchNameComponents = fullBranchName.split("/");
            String branchName = branchNameComponents[branchNameComponents.length - 1];

            long branchScore = mainBranchScore.getOrDefault(branchName.toLowerCase(), 0l);
            if (
                branchScore > bestBranchScore
                // if the current best branch has an unscored name, compare them
                // lexicographically (it may be a version number specification)
                || bestBranchScore <= 0 &&  branchName.compareTo(bestBranchName) > 0
            ) {
                bestBranchScore = branchScore;
                bestBranchName = branchName;
                bestBranch = succ;
            }
        }

        return bestBranch;
    }

    private static HashMap<Long, Contributor> discoverContributors(SwhBidirectionalGraph graph, long branch) {
        assert graph.getNodeType(branch) == SwhType.REV;
        HashMap<Long, Contributor> committers = new HashMap<Long, Contributor>();
        long commitCount = 0;

        // BFS to explore the given branch
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        LongOpenHashSet visited = new LongOpenHashSet();
        queue.enqueue(branch);
        while (!queue.isEmpty()) {
            long curr = queue.dequeueLong();
            commitCount++;

            LazyLongIterator it = graph.successors(curr);
            for (long succ; (succ = it.nextLong()) != -1;) {
                if (!visited.contains(succ)) {
                    if (graph.getNodeType(succ) == SwhType.REV) {
                        queue.enqueue(succ);
                        visited.add(succ);
                        Long committerId = graph.getCommitterId(succ);
                        if (committerId != null) {
                            // XXX: this ignores commits with an empty committer
                            // committer is valid, check the commit date
                            Contributor knownData = committers.get(committerId);
                            Date thisContributionDate = getCommitDate(graph, succ);

                            Contributor newData = new Contributor();
                            newData.committerId = committerId;
                            if (knownData == null) {
                                // first time we see that committer
                                newData.commitCount = 1;
                                newData.firstContributionDate = thisContributionDate;
                                newData.firstContributionNode = succ;
                            } else {
                                // commit from a committer we already saw
                                newData.commitCount = knownData.commitCount + 1;
                                if (thisContributionDate.before(knownData.firstContributionDate)) {
                                    // this commit is the oldest we've seen from
                                    // that committer
                                    newData.firstContributionDate = thisContributionDate;
                                    newData.firstContributionNode = succ;
                                }
                                else {
                                    // not an older commit, don't update firt
                                    // contribution
                                    newData.firstContributionDate = knownData.firstContributionDate;
                                    newData.firstContributionNode = knownData.firstContributionNode;
                                }
                            }
                            committers.put(committerId, newData);
                        }
                    }
                }
            }
        }

        // Identify core contributors
        for (var committer: committers.entrySet()) {
            if (committer.getValue().commitCount >= commitCount * 0.05) {
                committer.getValue().coreContributor = true;
            }
        }

        return committers;
    }

    private static void analyzeProject(SwhBidirectionalGraph graph, long snapshot) {
        assert graph.getNodeType(snapshot) == SwhType.SNP;
        long branch = findBestBranch(graph, snapshot);
        if (branch == -1) {
            return;
        }

        var uniqueCommitters = discoverContributors(graph, branch);
        long coreCommitters = 0;
        for (var committer: uniqueCommitters.entrySet()) {
            if (committer.getValue().coreContributor) {
                coreCommitters++;
            }
        }
        System.out.format(
            "%-6d unique committers, %-6d core in %s\n",
            uniqueCommitters.size(),
            coreCommitters,
            getOriginUrl(graph, getOriginOfSnapshot(graph, snapshot))
        );
        // TODO: extract research variables
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
                discoverNewOrigin(graph, node);
            }
        }

        for (long project : selectedProjects) {
            analyzeProject(graph, project);
        }

        System.out.format(
            "Analyzed %d projects (for %d different origins in the graph)\n",
            selectedProjects.size(),
            discoveredOrigins.size()
        );
    }
}
