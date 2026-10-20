package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

public class BackupFolderSelector {

    public static void showSelector(final Activity activity) {
        if (activity == null) return;

        final Set<String> selectedPaths = new HashSet<String>(BackupManager.getSelectedItems(activity));
        final Stack<FolderNode> navStack = new Stack<FolderNode>();

        final FolderNode rootNode = buildRootNode(activity);
        navStack.push(rootNode);

        final LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(24, 16, 24, 12);

        final LinearLayout topBar = new LinearLayout(activity);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, 0, 0, 12);

        final Button backBtn = new Button(activity);
        backBtn.setText("◀ Back");
        backBtn.setEnabled(false);

        final TextView pathView = new TextView(activity);
        pathView.setText("files/");
        pathView.setTextSize(14);
        pathView.setTypeface(null, Typeface.BOLD);
        pathView.setPadding(14, 0, 0, 0);
        pathView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        topBar.addView(backBtn);
        topBar.addView(pathView);

        final LinearLayout actionBar = new LinearLayout(activity);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setPadding(0, 0, 0, 10);

        final Button selectAllBtn = new Button(activity);
        selectAllBtn.setText("Select All");
        selectAllBtn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        final Button deselectAllBtn = new Button(activity);
        deselectAllBtn.setText("Deselect All");
        deselectAllBtn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        actionBar.addView(selectAllBtn);
        actionBar.addView(deselectAllBtn);

        final ListView listView = new ListView(activity);
        listView.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        listView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 880));

        container.addView(topBar);
        container.addView(actionBar);
        container.addView(listView);

        final SelectorAdapter adapter = new SelectorAdapter(activity, navStack.peek().children, selectedPaths);
        listView.setAdapter(adapter);

        final UIController controller = new UIController(activity, navStack, backBtn, pathView, adapter);

        backBtn.setOnClickListener(new BackBtnListener(navStack, controller));
        selectAllBtn.setOnClickListener(new SelectAllBtnListener(navStack, selectedPaths, adapter));
        deselectAllBtn.setOnClickListener(new DeselectAllBtnListener(navStack, selectedPaths, adapter));
        adapter.setOnNodeClickListener(new ItemClickListener(activity, navStack, controller));

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Select Files & Folders to Backup");
        builder.setView(container);
        builder.setPositiveButton("Save Selection", new SaveBtnListener(activity, selectedPaths));
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    public static class UIController {
        Activity activity;
        Stack<FolderNode> navStack;
        Button backBtn;
        TextView pathView;
        SelectorAdapter adapter;

        public UIController(Activity activity, Stack<FolderNode> navStack, Button backBtn, TextView pathView, SelectorAdapter adapter) {
            this.activity = activity;
            this.navStack = navStack;
            this.backBtn = backBtn;
            this.pathView = pathView;
            this.adapter = adapter;
        }

        public void updateUI() {
            FolderNode current = navStack.peek();
            backBtn.setEnabled(navStack.size() > 1);
            pathView.setText(current.displayPath);
            if (current.children == null) {
                current.children = loadDirectChildren(current);
            }
            adapter.updateItems(current.children);
        }
    }

    public static class BackBtnListener implements View.OnClickListener {
        Stack<FolderNode> navStack;
        UIController controller;
        public BackBtnListener(Stack<FolderNode> navStack, UIController controller) {
            this.navStack = navStack;
            this.controller = controller;
        }
        @Override
        public void onClick(View v) {
            if (navStack.size() > 1) {
                navStack.pop();
                controller.updateUI();
            }
        }
    }

    public static class SelectAllBtnListener implements View.OnClickListener {
        Stack<FolderNode> navStack;
        Set<String> selectedPaths;
        SelectorAdapter adapter;
        public SelectAllBtnListener(Stack<FolderNode> navStack, Set<String> selectedPaths, SelectorAdapter adapter) {
            this.navStack = navStack;
            this.selectedPaths = selectedPaths;
            this.adapter = adapter;
        }
        @Override
        public void onClick(View v) {
            FolderNode current = navStack.peek();
            if (current.children == null) {
                current.children = loadDirectChildren(current);
            }
            if (current.children != null) {
                for (FolderNode child : current.children) {
                    checkNode(child, selectedPaths);
                }
            }
            adapter.notifyDataSetChanged();
        }
    }

    public static class DeselectAllBtnListener implements View.OnClickListener {
        Stack<FolderNode> navStack;
        Set<String> selectedPaths;
        SelectorAdapter adapter;
        public DeselectAllBtnListener(Stack<FolderNode> navStack, Set<String> selectedPaths, SelectorAdapter adapter) {
            this.navStack = navStack;
            this.selectedPaths = selectedPaths;
            this.adapter = adapter;
        }
        @Override
        public void onClick(View v) {
            FolderNode current = navStack.peek();
            if (current.children == null) {
                current.children = loadDirectChildren(current);
            }
            if (current.children != null) {
                for (FolderNode child : current.children) {
                    uncheckNode(child, selectedPaths);
                }
            }
            adapter.notifyDataSetChanged();
        }
    }

    public static class ItemClickListener implements NodeClickListener {
        Activity activity;
        Stack<FolderNode> navStack;
        UIController controller;
        public ItemClickListener(Activity activity, Stack<FolderNode> navStack, UIController controller) {
            this.activity = activity;
            this.navStack = navStack;
            this.controller = controller;
        }
        @Override
        public void onNodeClicked(FolderNode node) {
            if (node.isDir) {
                if (node.children == null) {
                    node.children = loadDirectChildren(node);
                }
                if (!node.children.isEmpty()) {
                    navStack.push(node);
                    controller.updateUI();
                } else {
                    Toast.makeText(activity, "Folder is empty (" + node.name + ")", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    public static class SaveBtnListener implements DialogInterface.OnClickListener {
        Activity activity;
        Set<String> selectedPaths;
        public SaveBtnListener(Activity activity, Set<String> selectedPaths) {
            this.activity = activity;
            this.selectedPaths = selectedPaths;
        }
        @Override
        public void onClick(DialogInterface dialog, int which) {
            BackupManager.saveSelectedItems(activity, selectedPaths);
            Toast.makeText(activity, "Selection saved (" + selectedPaths.size() + " items chosen)", Toast.LENGTH_SHORT).show();
        }
    }

    public static boolean isNodeChecked(FolderNode node, Set<String> selected) {
        if (node == null) return false;
        if (selected.contains(node.relPath)) return true;
        FolderNode p = node.parent;
        while (p != null) {
            if (selected.contains(p.relPath)) return true;
            p = p.parent;
        }
        return false;
    }

    public static void checkNode(FolderNode node, Set<String> selected) {
        if (node == null) return;
        selected.add(node.relPath);
        if (node.children != null) {
            for (FolderNode child : node.children) {
                checkNode(child, selected);
            }
        }
        checkParentRollup(node.parent, selected);
    }

    public static void uncheckNode(FolderNode node, Set<String> selected) {
        if (node == null) return;
        selected.remove(node.relPath);
        if (node.children != null) {
            for (FolderNode child : node.children) {
                uncheckNode(child, selected);
            }
        }
        FolderNode p = node.parent;
        FolderNode childToExclude = node;
        while (p != null) {
            if (selected.remove(p.relPath)) {
                if (p.children != null) {
                    for (FolderNode sibling : p.children) {
                        if (!sibling.relPath.equals(childToExclude.relPath)) {
                            checkNode(sibling, selected);
                        }
                    }
                }
            }
            childToExclude = p;
            p = p.parent;
        }
    }

    private static void checkParentRollup(FolderNode parent, Set<String> selected) {
        if (parent == null || parent.relPath == null || parent.relPath.isEmpty()) return;
        if (parent.children != null && !parent.children.isEmpty()) {
            boolean allSelected = true;
            for (FolderNode child : parent.children) {
                if (!isNodeChecked(child, selected)) {
                    allSelected = false;
                    break;
                }
            }
            if (allSelected) {
                selected.add(parent.relPath);
                checkParentRollup(parent.parent, selected);
            }
        }
    }

    private static FolderNode buildRootNode(Context context) {
        File filesDir = null;
        if (Tools.DIR_GAME_HOME != null) {
            filesDir = new File(Tools.DIR_GAME_HOME);
        }
        if (filesDir == null || !filesDir.exists()) {
            filesDir = context.getExternalFilesDir(null);
        }

        FolderNode root = new FolderNode("Root", filesDir, "", true, "files/", 0, null);
        root.children = loadDirectChildren(root);
        return root;
    }

    public static List<FolderNode> loadDirectChildren(FolderNode parentNode) {
        List<FolderNode> list = new ArrayList<FolderNode>();
        if (parentNode == null || parentNode.file == null || !parentNode.file.isDirectory()) {
            return list;
        }

        try {
            File[] files = parentNode.file.listFiles();
            if (files != null) {
                for (File f : files) {
                    String name = f.getName();
                    if (name.equals(".cache")) continue;

                    boolean isDir = f.isDirectory();
                    String rel = parentNode.relPath.isEmpty() ? name : parentNode.relPath + "/" + name;
                    String disp = parentNode.displayPath + (parentNode.displayPath.endsWith("/") ? "" : "/") + name;
                    long size = isDir ? 0 : f.length();

                    FolderNode child = new FolderNode(name, f, rel, isDir, disp, size, parentNode);
                    list.add(child);
                }
            }
        } catch (Exception ignored) {}

        sortNodes(list);
        return list;
    }

    public static class NodeComparator implements Comparator<FolderNode> {
        @Override
        public int compare(FolderNode o1, FolderNode o2) {
            if (o1.isDir && !o2.isDir) return -1;
            if (!o1.isDir && o2.isDir) return 1;
            return o1.name.compareToIgnoreCase(o2.name);
        }
    }

    private static void sortNodes(List<FolderNode> list) {
        if (list == null) return;
        Collections.sort(list, new NodeComparator());
    }

    public static class FolderNode {
        public String name;
        public File file;
        public String relPath;
        public boolean isDir;
        public String displayPath;
        public long sizeBytes;
        public FolderNode parent;
        public List<FolderNode> children;

        public FolderNode(String name, File file, String relPath, boolean isDir, String displayPath, long sizeBytes, FolderNode parent) {
            this.name = name;
            this.file = file;
            this.relPath = relPath;
            this.isDir = isDir;
            this.displayPath = displayPath;
            this.sizeBytes = sizeBytes;
            this.parent = parent;
        }

        public String getSubtitle() {
            if (isDir) {
                if (children != null) {
                    int count = children.size();
                    return count + " item" + (count == 1 ? "" : "s");
                }
                return "Folder";
            }
            if (sizeBytes < 1024) return sizeBytes + " B";
            if (sizeBytes < 1048576) return String.format(java.util.Locale.US, "%.1f KB", sizeBytes / 1024.0);
            return String.format(java.util.Locale.US, "%.1f MB", sizeBytes / 1048576.0);
        }
    }

    public interface NodeClickListener {
        void onNodeClicked(FolderNode node);
    }

    public static class RowCheckedListener implements CompoundButton.OnCheckedChangeListener {
        FolderNode node;
        Set<String> selectedPaths;
        SelectorAdapter adapter;

        public RowCheckedListener(FolderNode node, Set<String> selectedPaths, SelectorAdapter adapter) {
            this.node = node;
            this.selectedPaths = selectedPaths;
            this.adapter = adapter;
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked) {
                checkNode(node, selectedPaths);
            } else {
                uncheckNode(node, selectedPaths);
            }
            adapter.notifyDataSetChanged();
        }
    }

    public static class RowClickListener implements View.OnClickListener {
        FolderNode node;
        NodeClickListener clickListener;

        public RowClickListener(FolderNode node, NodeClickListener clickListener) {
            this.node = node;
            this.clickListener = clickListener;
        }

        @Override
        public void onClick(View v) {
            if (clickListener != null && node.isDir) {
                clickListener.onNodeClicked(node);
            }
        }
    }

    private static class SelectorAdapter extends BaseAdapter {
        private final Context context;
        private List<FolderNode> items;
        private final Set<String> selectedPaths;
        private NodeClickListener clickListener;

        public SelectorAdapter(Context context, List<FolderNode> items, Set<String> selectedPaths) {
            this.context = context;
            this.items = items != null ? items : new ArrayList<FolderNode>();
            this.selectedPaths = selectedPaths;
        }

        public void updateItems(List<FolderNode> newItems) {
            this.items = newItems != null ? newItems : new ArrayList<FolderNode>();
            notifyDataSetChanged();
        }

        public void setOnNodeClickListener(NodeClickListener listener) {
            this.clickListener = listener;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public FolderNode getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            if (convertView == null) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(10, 10, 10, 10);

                CheckBox cb = new CheckBox(context);
                cb.setId(1001);
                cb.setFocusable(false);
                cb.setFocusableInTouchMode(false);

                LinearLayout textLayout = new LinearLayout(context);
                textLayout.setId(1005);
                textLayout.setOrientation(LinearLayout.VERTICAL);
                textLayout.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
                textLayout.setPadding(10, 4, 10, 4);

                TextView title = new TextView(context);
                title.setId(1002);
                title.setTextSize(15);
                title.setTypeface(null, Typeface.BOLD);

                TextView subtitle = new TextView(context);
                subtitle.setId(1003);
                subtitle.setTextSize(12);

                textLayout.addView(title);
                textLayout.addView(subtitle);

                Button openBtn = new Button(context);
                openBtn.setId(1004);
                openBtn.setText("Open ▶");
                openBtn.setTextSize(11);
                openBtn.setPadding(16, 4, 16, 4);
                openBtn.setFocusable(false);
                openBtn.setFocusableInTouchMode(false);

                row.addView(cb);
                row.addView(textLayout);
                row.addView(openBtn);
            } else {
                row = (LinearLayout) convertView;
            }

            final FolderNode node = getItem(position);
            CheckBox cb = row.findViewById(1001);
            TextView title = row.findViewById(1002);
            TextView subtitle = row.findViewById(1003);
            Button openBtn = row.findViewById(1004);
            LinearLayout textLayout = row.findViewById(1005);

            title.setText((node.isDir ? "📁 " : "📄 ") + node.name);
            subtitle.setText(node.getSubtitle());

            openBtn.setVisibility(node.isDir ? View.VISIBLE : View.GONE);

            cb.setOnCheckedChangeListener(null);
            cb.setChecked(isNodeChecked(node, selectedPaths));
            cb.setOnCheckedChangeListener(new RowCheckedListener(node, selectedPaths, this));

            RowClickListener clickListenerInstance = new RowClickListener(node, clickListener);
            openBtn.setOnClickListener(clickListenerInstance);
            textLayout.setOnClickListener(clickListenerInstance);
            row.setOnClickListener(clickListenerInstance);

            return row;
        }
    }
}
