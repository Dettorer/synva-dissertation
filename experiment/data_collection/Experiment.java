import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;

import org.softwareheritage.graph.SwhBidirectionalGraph;
import org.softwareheritage.graph.SwhType;
import org.softwareheritage.graph.labels.DirEntry;

import it.unimi.dsi.big.webgraph.LazyLongIterator;
import it.unimi.dsi.big.webgraph.labelling.ArcLabelledNodeIterator.LabelledArcIterator;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongStack;

public class Experiment {
    static class ProjectData {
        enum HasContributingStatus {
            // we are sure there are contributing guidelines
            TRUE,

            // we are sure there are NO contributing guidelines
            FALSE,

            // we didn't find a specific file for contributing guidelines but we found a
            // README, its content (identified by the url in readmeContentUrl) must be check
            // to know if there are contributing guidelines in the project
            CHECKREADMECONTENT
        };

        SwhBidirectionalGraph graph = null;
        long mainBranchNode = -1;
        long bestSnapshot = -1;
        boolean activeDuringStudiedTime = false;

        // people who contributed before the studied time (recently or not, overlaps with
        // recentContributors)
        HashSet<Long> oldContributors = new HashSet<Long>();
        // people who contributed *recently* before the studied time
        HashSet<Long> recentContributors = new HashSet<Long>();
        // people who contributed during the studied time
        HashSet<Long> studiedTimeContributors = new HashSet<Long>();

        HasContributingStatus hasContrib = HasContributingStatus.FALSE;
        String readmeContentUrl = null;
        long recentCommitCount = 0;
        long newContributorCount = 0;

        // constructor
        public ProjectData(SwhBidirectionalGraph graph, long branch, long snapshot) {
            this.graph = graph;
            this.mainBranchNode = branch;
            this.bestSnapshot = snapshot;
        }

        private void mineCommitHistory() {
            // BFS to explore the given branch
            LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
            LongOpenHashSet visited = new LongOpenHashSet();
            queue.enqueue(this.mainBranchNode);
            while (!queue.isEmpty()) {
                long currentRevision = queue.dequeueLong();

                // process commit metadata
                Long committerId = this.graph.getCommitterId(currentRevision);
                if (committerId != null) {
                    // XXX: we ignore commits with an empty committer (but still enqueue
                    // their parent commits)
                    Date contributionDate = getCommitDate(this.graph, currentRevision);
                    if (contributionDate.after(studiedTimeEnd.getTime())) {
                        // too recent, ignore but enqueue parent commits
                    }
                    else if (contributionDate.after(studiedTimeStart.getTime())) {
                        // committed during the studied period
                        this.activeDuringStudiedTime = true;
                        this.studiedTimeContributors.add(committerId);
                    } else if (contributionDate.after(recentReferenceTimeStart.getTime())) {
                        // committed in the recent period, count contribution in the recent
                        // commit count and in the recent and old unique contributor count
                        this.oldContributors.add(committerId);
                        this.recentContributors.add(committerId);
                        this.recentCommitCount++;
                    } else {
                        // older contribution, only save to make sure we don't count
                        // contributor as a *new* contributor during the studied period
                        this.oldContributors.add(committerId);
                    }
                }

                // enqueue parent commits
                LazyLongIterator it = this.graph.successors(currentRevision);
                for (long succ; (succ = it.nextLong()) != -1;) {
                    if (!visited.contains(succ)) {
                        if (this.graph.getNodeType(succ) == SwhType.REV) {
                            queue.enqueue(succ);
                            visited.add(succ);
                        }
                    }
                }
            }

            // wrap-up: count the new contributors during the studied time
            HashSet<Long> newContributors = new HashSet<Long>(this.studiedTimeContributors);
            newContributors.removeAll(this.oldContributors);
            this.newContributorCount = newContributors.size();
        }

        private void checkHasContributing() {
            long rootDir = findDirectoryOfRevision(this.graph, this.mainBranchNode);
            if (rootDir == -1) {
                // didn't even find a root directory, project is empty?
                System.err.format(
                    "Could not find a root directory for revision %d\n",
                    this.mainBranchNode
                );
                this.hasContrib = HasContributingStatus.FALSE;
                return;
            }

            // DFS
            // - if we find a file whose name is a variant of "CONTRIBUTING.md", we consider
            // the project has contributing guidelines;
            // - else, if we find a file whose name is a variant of "README.md", we build
            // and save an url that can fetch the content of that file (we will need to
            // query that later to check if the readme contains contributing guidelines);
            // - else, we consider the project doesn't have contributing guidelines.
            long firstReadmeFileFound = -1;
            LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
            LongOpenHashSet visited = new LongOpenHashSet();
            queue.enqueue(rootDir);
            visited.add(rootDir);
            while (!queue.isEmpty()) {
                long dir = queue.dequeueLong();

                // iterate over top-level entries in that directory
                LabelledArcIterator it = this.graph.labelledSuccessors(dir);
                for (long child; (child = it.nextLong()) != -1;) {
                    if (visited.contains(child)) {
                        continue;
                    }

                    if (this.graph.getNodeType(child) == SwhType.DIR) {
                        queue.enqueue(child);
                        visited.add(child);
                        continue;
                    } else if (this.graph.getNodeType(child) != SwhType.CNT) {
                        // this may be a REV node representing a git submodule, ignore
                        continue;
                    }

                    // Regular file, check its name
                    DirEntry[] labels = (DirEntry[]) it.label().get();
                    // multiple labels for the same CNT node means multiple files with
                    // identical content (multiple empty files for example)
                    for (DirEntry label: labels) {
                        String fileName = new String(
                            this.graph.getLabelName(label.filenameId)
                        );
                        if (isValidContributingFile(this.graph, child, fileName)) {
                            // bingo, we found explicit contributing guidelines
                            this.hasContrib = HasContributingStatus.TRUE;
                            return;
                        } else if (
                            firstReadmeFileFound == -1
                            && isValidReadmeFile(this.graph, child, fileName)
                        ) {
                            // this is the first usable README file we find, save its node
                            // in case we need to query it later (should we not find a
                            // CONTRIBUTING-style file)
                            firstReadmeFileFound = child;
                        }
                    }
                }
            }

            // If get this far, this means we found no valid CONTRIBUTING-style file, handle
            // the possible README or conclude that there is not contributing guidelines in
            // this project
            if (firstReadmeFileFound != -1) {
                this.readmeContentUrl = getFileContentQueryUrl(
                    this.graph,
                    firstReadmeFileFound
                );
                this.hasContrib = HasContributingStatus.CHECKREADMECONTENT;
            } else {
                this.hasContrib = HasContributingStatus.FALSE;
            }
        }

        public void analyze() {
            this.mineCommitHistory();

            if (this.activeDuringStudiedTime) {
                this.checkHasContributing();

                System.out.format(
                    "%d,%d,%s,%s,%d,%d,%s\n",
                    this.mainBranchNode,
                    this.newContributorCount,
                    this.hasContrib.toString(),
                    this.readmeContentUrl,
                    this.recentContributors.size(),
                    this.recentCommitCount,
                    getOriginUrl(
                        this.graph,
                        getOriginOfSnapshot(this.graph, this.bestSnapshot)
                    )
                );
            }
        }
    }

    // code from
    // https://docs.softwareheritage.org/devel/swh-graph/java-api.html#example-find-the-target-directory-of-a-revision
    private static long findDirectoryOfRevision(SwhBidirectionalGraph graph, long revNode) {
        assert graph.getNodeType(revNode) == SwhType.REV;

        LazyLongIterator it = graph.successors(revNode);
        for (long dst; (dst = it.nextLong()) != -1;) {
            if (graph.getNodeType(dst) == SwhType.DIR) {
                return dst;
            }
        }

        return -1;
    }

    // The studied time period, during which we evaluate the number of new contributors
    static Calendar recentReferenceTimeStart =
        new GregorianCalendar( 2019, Calendar.JANUARY, 1);

    // The "recent" time period relative to the studied period, during which we measure the
    // number of "recent" commits and unique contributors
    static Calendar studiedTimeStart = new GregorianCalendar(2019, Calendar.JUNE, 1);
    static Calendar studiedTimeEnd = new GregorianCalendar(2019, Calendar.SEPTEMBER, 1);

    // The set of ORI objects visited during the initial discovery
    private static LongOpenHashSet discoveredOrigins = new LongOpenHashSet();
    // The set of SNP objects selected as the entry points for project analysis
    private static Long2ObjectOpenHashMap<ProjectData> selectedProjects =
        new Long2ObjectOpenHashMap<ProjectData>();

    private static long getOriginOfSnapshot(SwhBidirectionalGraph graph, long snapshot) {
        LazyLongIterator it = graph.predecessors(snapshot);
        for (long succ; (succ = it.nextLong()) != -1;) {
            if (graph.getNodeType(succ) == SwhType.ORI) {
                return succ;
            }
        }

        System.err.format("no origin found for snapshot %d\n", snapshot);
        return -1;
    }

    // Code from
    // https://docs.softwareheritage.org/devel/swh-graph/java-api.html#example-find-all-the-shared-commit-forks-of-a-given-origin
    // 
    // Adapted to mark fork origins as discovered in discoveredOrigins and to return the
    // snapshot that has the longest chain of revisions to any root revision.
    //
    // Unfortunately this approach means that if multiple meaningfully different projects
    // share even only one commit somewhere (e.g. they all started as a fork of a common
    // small "template" project) in their history, we will consider them forks of the same
    // project and select only one of them for analysis, ignoring all the others.
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

        // Second traversal (backward graph): find all the origins containing any of these
        // root revisions and keep the farthest one.
        long farthestOrigin = -1;
        long largestDistance = -1;

        for (long rootRevision: backwardStack) {
            // BFS to find origins while making sure the last one we see is the farthest
            // from `rootRevision`
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
                            // and compare its distance to the farthest snapshot we found so
                            // far
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

    // If the given origin wasn't already discovered, find all the origins that have some
    // revisions in common with this one (forks) and select one representative among them
    // for later analysis.
    private static void discoverNewOrigin(SwhBidirectionalGraph graph, long origin) {
        if (discoveredOrigins.contains(origin)) {
            return;
        }
        discoveredOrigins.add(origin);

        long bestSnapshot = findLongestFork(graph, origin);
        if (bestSnapshot != -1) {
            // ignore buggy origins (softwareheritage sometimes have origin objects that
            // aren't actually archived, findLongestFork will return -1 for such buggy
            // origins)
            long bestBranch = findBestBranch(graph, bestSnapshot);
            if (bestBranch != -1) {
                ProjectData p = new ProjectData(graph, bestBranch, bestSnapshot);
                selectedProjects.put(bestBranch, p);
            }
        }
    }

    private static Date getCommitDate(SwhBidirectionalGraph graph, long src) {
        assert graph.getNodeType(src) == SwhType.REV;
        long timestamp = graph.getCommitterTimestamp(src);
        return new Date(timestamp * 1000);
    }

    // possible forms of a "contributing" file name or section name (in a readme), lowercase
    // and without extension. To compare with an observed file name, first remove the
    // extension, change to lowercase, then use `.contains()`.
    private static HashSet<String> contributingForms = new HashSet<String>() {{
        add("contributing");
        add("contribution");
        add("contribute");
        add("contrib");
    }};

    // check that the given file:
    // - has a name that is a variant of "CONTRIBUTING.md"
    // - is non-empty
    private static boolean isValidContributingFile(
        SwhBidirectionalGraph graph,
        long node,
        String fileName
    ) {
        String extensionLess = fileName.split("\\.")[0];
        Long contentLength = graph.getContentLength(node);
        return
            contributingForms.contains(extensionLess.toLowerCase())
            && contentLength != null
            && contentLength > 0;
    }

    // check that the given file:
    // - has a name that is a variant of "README.md"
    // - is non-empty
    // - has archived content that we can query later
    private static boolean isValidReadmeFile(
        SwhBidirectionalGraph graph,
        long node,
        String fileName
    ) {
        String extensionLess = fileName.trim().split("\\.")[0];
        Long contentLength = graph.getContentLength(node);

        return
            extensionLess.toLowerCase().equals("readme")
            && contentLength != null && contentLength > 0
            && !graph.isContentSkipped(node);
    }

    private static String getFileContentQueryUrl(
        SwhBidirectionalGraph graph,
        long cntNode
    ) {
        assert graph.getNodeType(cntNode) == SwhType.CNT;
        String hash = graph.getSWHID(cntNode).toString().split(":")[3];
        return String.format(
            "https://archive.softwareheritage.org/api/1/content/sha1_git:%s/raw/",
            hash
        );
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
                    // if no branch were found yet, use this one but keep the -1 score so
                    // that it's replaced by any named branch we may find later
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
                || bestBranchScore <= 0
                   && branchName.compareTo(bestBranchName) > 0
            ) {
                bestBranchScore = branchScore;
                bestBranchName = branchName;
                bestBranch = succ;
            }
        }

        return bestBranch;
    }

    public static void main(String[] args) throws Exception {
        // TODO: have a progress bar
        try {
            // graph loading
            String graphPath = args[1];
            SwhBidirectionalGraph graph;
            System.err.print("Loading the graph");
            if (args[0].equals("mapped")) {
                System.err.println(" (mapped)");
                graph = SwhBidirectionalGraph.loadLabelledMapped(graphPath);
            }
            else {
                System.err.println(" (in memory)");
                graph = SwhBidirectionalGraph.loadLabelled(graphPath);
            }
            graph.loadMessages();
            graph.loadCommitterTimestamps();
            graph.loadPersonIds();
            graph.loadLabelNames();
            graph.loadContentLength();
            graph.loadContentIsSkipped();

            // Project discovery and selection
            System.err.format("Starting traversal of %d nodes\n", graph.numNodes());
            for (long node = 0; node < graph.numNodes(); node++) {
                if (graph.getNodeType(node) == SwhType.ORI) {
                    discoverNewOrigin(graph, node);
                }
            }

            // Project mining
            System.out.println(
                "projectMainBranch,"
                + "newContributorCount,"
                + "hasContrib,"
                + "readmeUrl,"
                + "recentContributorCount,"
                + "recentCommitCount,"
                + "originUrl"
            );
            for (var entry: selectedProjects.long2ObjectEntrySet()) {
                entry.getValue().analyze();
            }

            System.err.format(
                "Analyzed %d projects (for %d different origins in the graph)\n",
                selectedProjects.size(),
                discoveredOrigins.size()
            );
        } catch(Exception e) {
            System.err.println("!!! Exception: " + e.getMessage());
            throw e;
        } catch(AssertionError e) {
            System.err.println("!!! AssertionError: " + e.getMessage());
            throw e;
        }
    }
}
