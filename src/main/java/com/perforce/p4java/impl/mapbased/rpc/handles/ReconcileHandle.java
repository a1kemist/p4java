package com.perforce.p4java.impl.mapbased.rpc.handles;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.perforce.p4java.diff.DigestTree;
import com.perforce.p4java.diff.StrStr;
import com.perforce.p4java.impl.mapbased.rpc.CommandEnv.RpcHandler;

public class ReconcileHandle extends AbstractHandle {

    private static final String RECONCILE_HANDLER_SKIP_ADD_KEY = "skipAdd";
    private static final String RECONCILE_DEL_COUNT_KEY = "delCount";
    private final Set<Integer> matchedIndex = new HashSet<>();
    private final DigestTree digestTree = new DigestTree();

    public ReconcileHandle(RpcHandler rpcHandler) {
        super(rpcHandler);
    }

    @Override
    public String getHandleType() {
        return "ReconcileHandle";
    }

    @SuppressWarnings("unchecked")
    public List<String> getSkipFiles() {
        if (!rpcHandler.getMap().containsKey(RECONCILE_HANDLER_SKIP_ADD_KEY)) {
            List<String> list = new LinkedList<String>();
            rpcHandler.getMap().put(RECONCILE_HANDLER_SKIP_ADD_KEY, list);
            return list;
        }
        return (List<String>) rpcHandler.getMap().get(RECONCILE_HANDLER_SKIP_ADD_KEY);
    }

    public void incrementDelCount() {
        long delCount = 0;
        if (rpcHandler.getMap().containsKey(RECONCILE_DEL_COUNT_KEY)) {
            delCount = (long) rpcHandler.getMap().get(RECONCILE_DEL_COUNT_KEY);
        }
        delCount++;
        rpcHandler.getMap().put(RECONCILE_DEL_COUNT_KEY, delCount);
    }

    public long getDelCount() {
        if (rpcHandler.getMap().containsKey(RECONCILE_DEL_COUNT_KEY)) {
            return (long) rpcHandler.getMap().get(RECONCILE_DEL_COUNT_KEY);
        }
        return 0;
    }

    public void setMatch(int i) {
        this.matchedIndex.add(i);
    }

    public boolean alreadyMatched(int i) {
        return this.matchedIndex.contains(i);
    }

    public String getDigest(String fileName, String digestStr) {
        digestTree.putIfAbsent(new StrStr(fileName, digestStr));
        return digestTree.get(fileName).getDigest();
    }

    public DigestTree getDigestTree() {
        return this.digestTree;
    }
}
