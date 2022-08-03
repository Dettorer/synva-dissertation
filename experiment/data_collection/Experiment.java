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
    static class ContributorData {
        long committerId;
        long firstContributionNode;
        Date firstContributionDate;
        long commitCount;
        boolean coreContributor = false;
    };

    static class ProjectData {
        enum HasContributingStatus {
            // we are sure there are contributing guidelines
            TRUE,

            // we are sure there are NO contributing guidelines
            FALSE,

            // we didn't find a specific file for contributing guidelines but we
            // found a README, its content (identified by the url in
            // readmeContentUrl) must be check to know if there are contributing
            // guidelines in the project
            CHECKREADMECONTENT
        };

        long mainBranchNode;
        long bestSnapshot;
        long commitCount;
        HashMap<Long, ContributorData> committers;
        boolean activeDuringStudiedTime;

        // research variables
        HasContributingStatus hasContrib = HasContributingStatus.FALSE;
        String readmeContentUrl = null;

        // constructors
        public ProjectData() {
            mainBranchNode = -1;
            bestSnapshot = -1;
            commitCount = 0;
            committers = new HashMap<Long, ContributorData>();
            activeDuringStudiedTime = false;
        }

        public ProjectData(long branch, long snapshot) {
            this();
            mainBranchNode = branch;
            bestSnapshot = snapshot;
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

    static Calendar studiedTimeStart = new GregorianCalendar(2019, Calendar.JANUARY, 1);
    static Calendar studiedTimeEnd = new GregorianCalendar(2019, Calendar.JUNE, 1);

    // The set of ORI objects visited during the initial discovery
    private static LongOpenHashSet discoveredOrigins = new LongOpenHashSet();
    // The set of SNP objects selected as the entry points for project analysis
    private static Long2ObjectOpenHashMap<ProjectData> selectedProjects = new Long2ObjectOpenHashMap<ProjectData>();

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
                            // we found so far
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
            long bestBranch = findBestBranch(graph, bestSnapshot);
            if (bestBranch != -1) {
                ProjectData p = new ProjectData(bestBranch, bestSnapshot);
                selectedProjects.put(bestBranch, p);
            }
        }
    }

    private static Date getCommitDate(SwhBidirectionalGraph graph, long src) {
        assert graph.getNodeType(src) == SwhType.REV;
        long timestamp = graph.getCommitterTimestamp(src);
        return new Date(timestamp * 1000);
    }

    // possible forms of a "contributing" file name or section name (in a
    // readme), lowercase and without extension. To compare with an observed
    // file name, first remove the extension, change to lowercase, then use
    // .contains().
    private static HashSet<String> contributingForms = new HashSet<String>() {{
        add("contributing");
        add("contribution");
        add("contribute");
        add("contrib");
    }};

    // check that the given file:
    // - has a name that is a variant of "CONTRIBUTING.md"
    // - is non-empty
    private static boolean isValidContributingFile(SwhBidirectionalGraph graph, long node, String fileName) {
        String extensionLess = fileName.split("\\.")[0];
        Long contentLength = graph.getContentLength(node);
        return
            contributingForms.contains(extensionLess.toLowerCase())
            && contentLength != null && contentLength > 0;
    }

    // check that the given file:
    // - has a name that is a variant of "README.md"
    // - is non-empty
    // - has archived content that we can query later
    private static boolean isValidReadmeFile(SwhBidirectionalGraph graph, long node, String fileName) {
        String extensionLess = fileName.trim().split("\\.")[0];
        Long contentLength = graph.getContentLength(node);

        // if (extensionLess.toLowerCase().equals("readme")) {
            // if (contentLength == null) {
                // System.err.println(">>> content length is null");
            // } else {
                // System.err.println(">>> empty file");
            // }

            // if (graph.isContentSkipped(node)) {
                // System.err.println(">>> content skipped");
            // }
        // }

        return
            extensionLess.toLowerCase().equals("readme")
            && contentLength != null && contentLength > 0
            && !graph.isContentSkipped(node);
    }

    private static void checkProjectHasContributing(SwhBidirectionalGraph graph, ProjectData project) {
        long rootDir = findDirectoryOfRevision(graph, project.mainBranchNode);
        if (rootDir == -1) {
            // didn't even find a root directory, project is empty?
            System.err.format("Could not find a root directory for revision %d\n", project.mainBranchNode);
            project.hasContrib = ProjectData.HasContributingStatus.FALSE;
            return;
        }

        // DFS
        // - if we find a file whose name is a variant of "CONTRIBUTING.md", we
        // consider the project has contributing guidelines;
        // - else, if we find a file whose name is a variant of "README.md", we
        // build and save an url that can fetch the content of that file (we
        // will need to query that later to check if the readme contains
        // contributing guidelines);
        // - else, we consider the project doesn't have contributing guidelines.
        long firstReadmeFileFound = -1;
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        LongOpenHashSet visited = new LongOpenHashSet();
        queue.enqueue(rootDir);
        visited.add(rootDir);
        while (!queue.isEmpty()) {
            long dir = queue.dequeueLong();

            // iterate over top-level entries in that directory
            LabelledArcIterator it = graph.labelledSuccessors(dir);
            for (long child; (child = it.nextLong()) != -1;) {
                if (visited.contains(child)) {
                    continue;
                }

                if (graph.getNodeType(child) == SwhType.DIR) {
                    queue.enqueue(child);
                    visited.add(child);
                    continue;
                } else if (graph.getNodeType(child) != SwhType.CNT) {
                    // this may be a REV node representing a git submodule,
                    // ignore
                    continue;
                }

                // Regular file, check its name
                DirEntry[] labels = (DirEntry[]) it.label().get();
                // multiple labels for the same CNT node means multiple files with
                // identical content (multiple empty files for example)
                for (DirEntry label: labels) {
                    String fileName = new String(graph.getLabelName(label.filenameId));
                    if (isValidContributingFile(graph, child, fileName)) {
                        // bingo, we found explicit contributing guidelines
                        project.hasContrib = ProjectData.HasContributingStatus.TRUE;
                        return;
                    } else if (
                        firstReadmeFileFound == -1
                        && isValidReadmeFile(graph, child, fileName)
                    ) {
                        // this is the first usable README file we find, save
                        // its node in case we need to query it later (should we
                        // not find a CONTRIBUTING-style file)
                        firstReadmeFileFound = child;
                    }
                }
            }
        }

        // If get this far, this means we found no valid CONTRIBUTING-style file,
        // handle the possible README or conclude that there is not contributing
        // guidelines in this project
        if (firstReadmeFileFound != -1) {
            project.readmeContentUrl = getFileContentQueryUrl(graph, firstReadmeFileFound);
            project.hasContrib = ProjectData.HasContributingStatus.CHECKREADMECONTENT;
        } else {
            project.hasContrib = ProjectData.HasContributingStatus.FALSE;
        }
    }

    private static String getFileContentQueryUrl(SwhBidirectionalGraph graph, long cntNode) {
        assert graph.getNodeType(cntNode) == SwhType.CNT;
        String hash = graph.getSWHID(cntNode).toString().split(":")[3];
        return String.format("https://archive.softwareheritage.org/api/1/content/sha1_git:%s/raw/", hash);
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

    private static void mineCommitMetadata(SwhBidirectionalGraph graph, ProjectData project) {
        // BFS to explore the given branch
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        LongOpenHashSet visited = new LongOpenHashSet();
        queue.enqueue(project.mainBranchNode);
        while (!queue.isEmpty()) {
            long curr = queue.dequeueLong();
            project.commitCount++;

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
                            ContributorData knownData = project.committers.get(committerId);
                            Date thisContributionDate = getCommitDate(graph, succ);

                            // check project activity in studied time
                            if (
                                thisContributionDate.after(studiedTimeStart.getTime())
                                && thisContributionDate.before(studiedTimeEnd.getTime())
                            ) {
                                project.activeDuringStudiedTime = true;
                            }

                            ContributorData newData = new ContributorData();
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
                            project.committers.put(committerId, newData);
                        }
                    }
                }
            }
        }

        // Identify core contributors
        for (var committer: project.committers.entrySet()) {
            if (committer.getValue().commitCount >= project.commitCount * 0.05) {
                committer.getValue().coreContributor = true;
            }
        }
    }

    private static void analyzeProject(SwhBidirectionalGraph graph, ProjectData project) {
        mineCommitMetadata(graph, project);
        long coreCommitters = 0;
        for (var committer: project.committers.entrySet()) {
            if (committer.getValue().coreContributor) {
                coreCommitters++;
            }
        }

        if (project.activeDuringStudiedTime) {
            checkProjectHasContributing(graph, project);
            System.out.format(
                "%d,%d,%s,%s,%d,%d,%s\n",
                project.mainBranchNode,
                -1, // TODO: new contributor measure
                project.hasContrib.toString(),
                project.readmeContentUrl,
                -1, // TODO: recent contributor count
                -1, // TODO: recent commit count
                getOriginUrl(graph, getOriginOfSnapshot(graph, project.bestSnapshot))
            );
        }
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
            System.out.println("project_id,new_contributors,has_contrib,readme_url,contributor_count,commit_count,origin_url");
            for (var entry: selectedProjects.long2ObjectEntrySet()) {
                analyzeProject(graph, entry.getValue());
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
