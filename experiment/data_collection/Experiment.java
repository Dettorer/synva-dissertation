import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.softwareheritage.graph.SwhBidirectionalGraph;
import org.softwareheritage.graph.SwhType;
import org.softwareheritage.graph.labels.DirEntry;

import it.unimi.dsi.big.webgraph.LazyLongIterator;
import it.unimi.dsi.big.webgraph.labelling.ArcLabelledNodeIterator.LabelledArcIterator;
import it.unimi.dsi.fastutil.BigArrays;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongStack;
import it.unimi.dsi.fastutil.shorts.ShortBigArrays;
import it.unimi.dsi.logging.ProgressLogger;

public class Experiment {
    private static final Logger LOGGER = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    // The set of ORI objects visited during the initial discovery
    private static short[][] discoveredOrigins;
    // The set of project (represented by the node of their best snapshot) selected for data
    // collection
    private static final LongOpenHashSet selectedProjects = new LongOpenHashSet();
    private static final LongArrayList selectedProjectsList = new LongArrayList();

    // The studied time period, during which we evaluate the number of new contributors
    private static final Calendar recentReferenceTimeStart =
        new GregorianCalendar( 2019, Calendar.JANUARY, 1);

    // possible forms of a "contributing" file name or section name (in a readme), lowercase
    // and without extension. To compare with an observed file name, first remove the
    // extension, change to lowercase, then use `.contains()`.
    private static HashSet<String> contributingForms = new HashSet<String>() {{
        add("contributing");
        add("contribution");
        add("contribute");
        add("contrib");
    }};

    // The "recent" time period relative to the studied period, during which we measure the
    // number of "recent" commits and unique contributors
    private static final Calendar studiedTimeStart =
        new GregorianCalendar(2019, Calendar.JUNE, 1);
    private static final Calendar studiedTimeEnd =
        new GregorianCalendar(2019, Calendar.SEPTEMBER, 1);

    /** A Linear Feedback Shift Register
     *
     * Generates a random uniform permutation of [0, n] as a *stream* that takes O(1) space.
     * Because the entire state is a single long integer, the order for a given number of bits is deterministic.
     *
     * https://en.wikipedia.org/wiki/Linear-feedback_shift_register
     * https://stackoverflow.com/a/32193751
     * https://www.uobabylon.edu.iq/eprints/paper_1_17528_649.pdf
     * http://users.ece.cmu.edu/~koopman/lfsr/
     * https://docs.xilinx.com/v/u/en-US/xapp052
     */
    public static class LinearFeedbackShiftRegister implements LazyLongIterator {
        private final long n;
        private long state;
        private final int numBits;

        public LinearFeedbackShiftRegister(long n, long seed) {
            if (n <= 0) {
                throw new IllegalArgumentException("n must be >= 0");
            }
            this.n = n;
            this.state = (seed % n) + 1;
            this.numBits = ((int) (Math.log(n) / Math.log(2))) + 1;
        }

        public LinearFeedbackShiftRegister(long n) {
            this(n, (new Random()).nextLong());
        }

        @Override
        public long nextLong() {
            do {
                long bits = state;
                for (int s : SHIFT_AMT[numBits]) {
                    bits ^= (state >> s);
                }
                state = ((state >> 1) | ((bits & 1) << (numBits - 1)));
            } while (state > n);
            return state - 1;
        }

        @Override
        public long skip(long l) {
            long i = 0;
            while (i < l && nextLong() != -1)
                i++;
            return i;
        }

        /** Maximal Length LFSR Feedback Terms (coefficients of primitive polynomials) for
         * each bit length
         */
        private static final int[][] SHIFT_AMT = new int[][] {
            // Taps taken from https://docs.xilinx.com/v/u/en-US/xapp052
            // For each k, numBits - k is the coefficient of the primitive polynomial.
            // numBits is also a coefficient.
            // Example: for numBits = 16, the array contains 1, 3, 12, which means the
            // coefficients are:
            // {16, 16 - 1, 16 - 3, 16 - 12} = {16, 15, 13, 4}
            //
            // p = [l.strip().split() for l in open('lol').readlines()]
            // p = [(int(l[0]), list(map(int, l[1].split(',')))) for l in p]
            // p = [[l[0] - x for x in l[1]] for l in p]
            new int[]{},
            new int[]{1},
            new int[]{1},
            new int[]{1},
            new int[]{1},
            new int[]{2},
            new int[]{1},
            new int[]{1},
            new int[]{2, 3, 4},
            new int[]{4},
            new int[]{3},
            new int[]{2},
            new int[]{6, 8, 11},
            new int[]{9, 10, 12},
            new int[]{9, 11, 13},
            new int[]{1},
            new int[]{1, 3, 12},
            new int[]{3},
            new int[]{7},
            new int[]{13, 17, 18},
            new int[]{3},
            new int[]{2},
            new int[]{1},
            new int[]{5},
            new int[]{1, 2, 7},
            new int[]{3},
            new int[]{20, 24, 25},
            new int[]{22, 25, 26},
            new int[]{3},
            new int[]{2},
            new int[]{24, 26, 29},
            new int[]{3},
            new int[]{10, 30, 31},
            new int[]{13},
            new int[]{7, 32, 33},
            new int[]{2},
            new int[]{11},
            new int[]{32, 33, 34, 35, 36},
            new int[]{32, 33, 37},
            new int[]{4},
            new int[]{2, 19, 21},
            new int[]{3},
            new int[]{1, 22, 23},
            new int[]{1, 5, 6},
            new int[]{1, 26, 27},
            new int[]{1, 3, 4},
            new int[]{1, 20, 21},
            new int[]{5},
            new int[]{1, 27, 28},
            new int[]{9},
            new int[]{1, 26, 27},
            new int[]{1, 15, 16},
            new int[]{3},
            new int[]{1, 15, 16},
            new int[]{1, 36, 37},
            new int[]{24},
            new int[]{1, 21, 22},
            new int[]{7},
            new int[]{19},
            new int[]{1, 21, 22},
            new int[]{1},
            new int[]{1, 15, 16},
            new int[]{1, 56, 57},
            new int[]{1},
            new int[]{1, 3, 4},
        };
    }

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
                LOGGER.warn(
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

                synchronized (System.out) {
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
    }

    private static boolean originIsDiscovered(long oriNode) {
        return BigArrays.get(discoveredOrigins, oriNode) == 1;
    }

    private static void markOriginAsDiscovered(long oriNode) {
        BigArrays.set(discoveredOrigins, oriNode, (short)1);
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

    private static long getOriginOfSnapshot(SwhBidirectionalGraph graph, long snapshot) {
        LazyLongIterator it = graph.predecessors(snapshot);
        for (long succ; (succ = it.nextLong()) != -1;) {
            if (graph.getNodeType(succ) == SwhType.ORI) {
                return succ;
            }
        }

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
                        visited.add(succ);
                        if (graph.getNodeType(succ) == SwhType.SNP) {
                            // Found a snapshot, mark its origin as discovered
                            long origin = getOriginOfSnapshot(graph, succ);
                            if (origin != -1) {
                                markOriginAsDiscovered(origin);
                            }
                            // and compare its distance to the farthest snapshot we found so
                            // far
                            if (currentDistance > largestDistance) {
                                farthestOrigin = succ;
                                largestDistance = currentDistance;
                            }
                        } else if (graph.getNodeType(succ) == SwhType.REV) {
                            // do not enqueue anything else than REV nodes
                            queue.enqueue(succ);
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
    private static void discoverProject(SwhBidirectionalGraph graph, long origin) {
        assert graph.getNodeType(origin) == SwhType.ORI;

        long bestSnapshot = findLongestFork(graph, origin);
        if (bestSnapshot != -1) {
            // ignore buggy origins (softwareheritage sometimes have origin objects that
            // aren't actually archived, findLongestFork will return -1 for such buggy
            // origins)
            synchronized (selectedProjects) {
                if (selectedProjects.add(bestSnapshot)) {
                    selectedProjectsList.add(bestSnapshot);
                }
            }
        }
    }

    private static void collectProject(SwhBidirectionalGraph graph, long snapshot) {
        assert graph.getNodeType(snapshot) == SwhType.SNP;

        long bestBranch = findBestBranch(graph, snapshot);
        if (bestBranch != -1) {
            ProjectData p = new ProjectData(graph, bestBranch, snapshot);
            p.analyze();
        }
    }

    private static Date getCommitDate(SwhBidirectionalGraph graph, long src) {
        assert graph.getNodeType(src) == SwhType.REV;
        long timestamp = graph.getCommitterTimestamp(src);
        return new Date(timestamp * 1000);
    }

    // check that the given file:
    // - has a name that is a variant of "CONTRIBUTING.md"
    // - is non-empty
    private static boolean isValidContributingFile(
        SwhBidirectionalGraph graph,
        long node,
        String fileName
    ) {
        String[] splitted = fileName.trim().split("\\.");
        if (splitted.length <= 0) {
            return false;
        }

        String extensionLess = splitted[0];
        Long contentLength = graph.getContentLength(node);
        return
            contentLength != null
            && contentLength > 0
            && contributingForms.contains(extensionLess.toLowerCase());
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
        String[] splitted = fileName.trim().split("\\.");
        if (splitted.length <= 0) {
            return false;
        }

        String extensionLess = splitted[0];
        Long contentLength = graph.getContentLength(node);
        return
            contentLength != null
            && contentLength > 0
            && extensionLess.toLowerCase().equals("readme")
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
        try {
            ProgressLogger pl = new ProgressLogger(LOGGER, 1, TimeUnit.SECONDS, "nodes");

            // argument "parsing"
            if (args.length < 2 || args.length > 3) {
                System.err.format(
                    "usage: java [java options] %s.java"
                        + " memory|mapped <graph path> [thread count]\n",
                    Experiment.class.getName()
                );
                System.exit(1);
            }
            String loadMode = args[0];
            String graphPath = args[1];
            int threadCount = Runtime.getRuntime().availableProcessors(); // default
            if (args.length == 3) {
                // a non-default thread count was given
                try {
                    threadCount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    LOGGER.error("Invalid number for thread count \"{}\"", args[2]);
                    System.exit(1);
                }
            }
            else {
                LOGGER.info("Using the default thread count of {}", threadCount);
            }


            // graph loading
            SwhBidirectionalGraph graph;
            LOGGER.info("Loading the graph");
            if (loadMode.equals("mapped")) {
                graph = SwhBidirectionalGraph.loadLabelledMapped(graphPath, pl);
            }
            else {
                graph = SwhBidirectionalGraph.loadLabelled(graphPath, pl);
            }
            
            graph.loadMessages();
            graph.loadCommitterTimestamps();
            graph.loadPersonIds();
            graph.loadLabelNames();
            graph.loadContentLength();
            graph.loadContentIsSkipped();
            long numNodes = graph.numNodes();

            // randomize the order in which we will test each node
            LinearFeedbackShiftRegister permutation =
                new LinearFeedbackShiftRegister(numNodes, 1);
            // FIXME: we skip projects if the division truncates
            long nodesPerThread = numNodes / threadCount;

            final ExecutorService discoveryService
                = Executors.newFixedThreadPool(threadCount);

            AtomicInteger discoveryNextThreadId = new AtomicInteger(0);
            ThreadLocal<Integer> discoveryThreadLocalId =
                ThreadLocal.withInitial(discoveryNextThreadId::getAndIncrement);

            // Project discovery and selection
            discoveredOrigins = ShortBigArrays.newBigArray(numNodes);
            pl.itemsName = "ORI nodes";
            pl.expectedUpdates = numNodes / 144; // an estimated 0.7% of nodes are ORI
            pl.start("Discovering projects");
            for (int i = 0; i < threadCount; ++i) {
                discoveryService.submit(() -> {
                    try {
                        int threadId = discoveryThreadLocalId.get();
                        SwhBidirectionalGraph g = graph.copy();
                        for (
                            long n = nodesPerThread * threadId;
                            n < Math.min(nodesPerThread * (threadId + 1), numNodes);
                            n++
                        ) {
                                long node;
                                synchronized (permutation) {
                                    node = permutation.nextLong();
                                }
                                if (node < 0 || node >= numNodes) {
                                    LOGGER.error(
                                        "invalid node {} given by the shift register",
                                        node
                                    );
                                    assert false;
                                }
                                else if (
                                    g.getNodeType(node) == SwhType.ORI
                                    && !originIsDiscovered(node)
                                ) {
                                    markOriginAsDiscovered(node);
                                    discoverProject(g, node);
                                    synchronized (pl) {
                                        pl.lightUpdate();
                                    }
                                }
                        }
                    } catch(Exception e) {
                        LOGGER.error("exception: {}", e.getMessage());
                        e.printStackTrace();
                    } catch(AssertionError e) {
                        LOGGER.error("assertion error: {}", e.getMessage());
                        e.printStackTrace();
                    }
                });
            }

            discoveryService.shutdown();
            discoveryService.awaitTermination(365, TimeUnit.DAYS);
            pl.done();

            // Project analysis
            int projectCount = selectedProjects.size();
            // FIXME: we skip projects if the division truncates
            int projectPerThread = projectCount / threadCount;
            AtomicInteger collectionNextThreadId = new AtomicInteger(0);
            ThreadLocal<Integer> collectionThreadLocalId =
                ThreadLocal.withInitial(collectionNextThreadId::getAndIncrement);

            System.out.println(
                "projectMainBranch,"
                + "newContributorCount,"
                + "hasContrib,"
                + "readmeUrl,"
                + "recentContributorCount,"
                + "recentCommitCount,"
                + "originUrl"
            );
            final ExecutorService collectionService
                = Executors.newFixedThreadPool(threadCount);
            pl.itemsName = "projects";
            pl.expectedUpdates = selectedProjects.size();
            pl.start("Collecting project data");
            for (int i = 0; i < threadCount; ++i) {
                collectionService.submit(() -> {
                    try {
                        int threadId = collectionThreadLocalId.get();
                        SwhBidirectionalGraph g = graph.copy();
                        for (
                            int n = projectPerThread * threadId;
                            n < Math.min(projectPerThread * (threadId + 1), projectCount);
                            n++
                        ) {
                            collectProject(g, selectedProjectsList.getLong(n));
                            synchronized (pl) {
                                pl.lightUpdate();
                            }
                        }
                    } catch(Exception e) {
                        LOGGER.error("exception: {}", e.getMessage());
                        e.printStackTrace();
                    } catch(AssertionError e) {
                        LOGGER.error("assertion error: {}", e.getMessage());
                        e.printStackTrace();
                    }
                });
            }
            collectionService.shutdown();
            collectionService.awaitTermination(365, TimeUnit.DAYS);
            pl.done();
        } catch(Exception e) {
            LOGGER.error("Exception: {}", e.getMessage());
            throw e;
        } catch(AssertionError e) {
            LOGGER.error("AssertionError: {}", e.getMessage());
            throw e;
        }
    }
}
