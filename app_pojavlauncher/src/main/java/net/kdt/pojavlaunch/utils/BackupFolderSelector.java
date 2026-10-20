package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
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

        // Container
        final LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(24, 16, 24, 12);

        // Notice Bar: Accounts & Settings are auto-backed up
        final TextView noticeView = new TextView(activity);
        noticeView.setText("ℹ️ Accounts & Settings are always backed up automatically.");
        noticeView.setTextSize(12);
        noticeView.setTextColor(Color.parseColor("#4CAF50"));
        noticeView.setPadding(4, 0, 4, 10);
        container.addView(noticeView);

        // Top Navigation Bar
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

        // Action Buttons Bar (Select All / Deselect All)
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

        // Items List
        final ListView listView = new ListView(activity);
        listView.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        listView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 860));

        container.addView(topBar);
        container.addView(actionBar);
        container.addView(listView);

        final SelectorAdapter adapter = new SelectorAdapter(activity, navStack.peek().children, selectedPaths);
        listView.setAdapter(adapter);

        final Runnable updateUI = new Runnable() {
            @Override
            public void run() {
                FolderNode current = navStack.peek();
                backBtn.setEnabled(navStack.size() > 1);
                pathView.setText(current.displayPath);
                adapter.updateItems(current.children);
            }
        };

        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (navStack.size() > 1) {
                    navStack.pop();
                    updateUI.run();
                }
            }
        });

        selectAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FolderNode current = navStack.peek();
                if (current.children != null) {
                    for (FolderNode child : current.children) {
                        selectRecursively(child, selectedPaths);
                    }
                }
                adapter.notifyDataSetChanged();
            }
        });

        deselectAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FolderNode current = navStack.peek();
                if (current.children != null) {
                    for (FolderNode child : current.children) {
                        deselectRecursively(child, selectedPaths);
                    }
                }
                adapter.notifyDataSetChanged();
            }
        });

        adapter.setOnNodeClickListener(new NodeClickListener() {
            @Override
            public void onNodeClicked(FolderNode node) {
                if (node.isDir) {
                    if (node.children != null && !node.children.isEmpty()) {
                        navStack.push(node);
                        updateUI.run();
                    } else {
                        Toast.makeText(activity, "Folder is empty (" + node.name + ")", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Select Files & Folders to Backup");
        builder.setView(container);
        builder.setPositiveButton("Save Selection", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                BackupManager.saveSelectedItems(activity, selectedPaths);
                Toast.makeText(activity, "Selection saved (" + selectedPaths.size() + " items chosen)", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    public static void selectRecursively(FolderNode node, Set<String> selected) {
        if (node == null) return;
        selected.add(node.relPath);
        if (node.children != null) {
            for (FolderNode child : node.children) {
                selectRecursively(child, selected);
            }
        }
        checkParentSelection(node.parent, selected);
    }

    public static void deselectRecursively(FolderNode node, Set<String> selected) {
        if (node == null) return;
        selected.remove(node.relPath);
        if (node.children != null) {
            for (FolderNode child : node.children) {
                deselectRecursively(child, selected);
            }
        }
        FolderNode p = node.parent;
        while (p != null) {
            selected.remove(p.relPath);
            p = p.parent;
        }
    }

    private static void checkParentSelection(FolderNode parent, Set<String> selected) {
        if (parent == null || parent.relPath == null || parent.relPath.isEmpty()) return;
        boolean allSelected = true;
        if (parent.children != null && !parent.children.isEmpty()) {
            for (FolderNode child : parent.children) {
                if (!selected.contains(child.relPath)) {
                    allSelected = false;
                    break;
                }
            }
        }
        if (allSelected) {
            selected.add(parent.relPath);
            checkParentSelection(parent.parent, selected);
        }
    }

    private static FolderNode buildRootNode(Context context) {
        FolderNode root = new FolderNode("Root", "", true, "files/");
        root.children = new ArrayList<FolderNode>();

        try {
            File filesDir = null;
            if (Tools.DIR_GAME_HOME != null) {
                filesDir = new File(Tools.DIR_GAME_HOME);
            }
            if (filesDir == null || !filesDir.exists()) {
                filesDir = context.getExternalFilesDir(null);
            }

            if (filesDir != null && filesDir.exists()) {
                File[] rootFiles = filesDir.listFiles();
                if (rootFiles != null) {
                    for (File f : rootFiles) {
                        String name = f.getName();
                        if (name.equals(".cache")) continue;
                        FolderNode node = scanNode(f, name, name, root, 0);
                        root.children.add(node);
                    }
                }
            }
        } catch (Exception ignored) {}

        sortNodes(root.children);
        return root;
    }

    private static FolderNode scanNode(File file, String relPath, String displayPath, FolderNode parent, int depth) {
        boolean isDir = file.isDirectory();
        long size = 0;
        try {
            size = isDir ? 0 : file.length();
        } catch (Exception ignored) {}

        FolderNode node = new FolderNode(file.getName(), relPath, isDir, displayPath, size, parent);

        if (isDir) {
            node.children = new ArrayList<FolderNode>();
            if (depth > 12) {
                return node;
            }

            try {
                File[] sub = file.listFiles();
                if (sub != null) {
                    for (File sf : sub) {
                        String subName = sf.getName();
                        // Skip static assets & libraries so it doesn't clutter list with 100k internal hash files
                        if (relPath.equals(".minecraft") && (subName.equals("assets") || subName.equals("libraries") || subName.equals(".fabric") || subName.equals("logs"))) {
                            continue;
                        }
                        String childRel = relPath + "/" + subName;
                        String childDisplay = displayPath + " / " + subName;
                        node.children.add(scanNode(sf, childRel, childDisplay, node, depth + 1));
                    }
                    sortNodes(node.children);
                }
            } catch (Exception ignored) {}
        }

        return node;
    }

    private static void sortNodes(List<FolderNode> list) {
        if (list == null) return;
        Collections.sort(list, new Comparator<FolderNode>() {
            @Override
            public int compare(FolderNode o1, FolderNode o2) {
                if (o1.isDir && !o2.isDir) return -1;
                if (!o1.isDir && o2.isDir) return 1;
                return o1.name.compareToIgnoreCase(o2.name);
            }
        });
    }

    public static class FolderNode {
        public String name;
        public String relPath;
        public boolean isDir;
        public String displayPath;
        public long sizeBytes;
        public FolderNode parent;
        public List<FolderNode> children;

        public FolderNode(String name, String relPath, boolean isDir, String displayPath) {
            this(name, relPath, isDir, displayPath, 0, null);
        }

        public FolderNode(String name, String relPath, boolean isDir, String displayPath, long sizeBytes, FolderNode parent) {
            this.name = name;
            this.relPath = relPath;
            this.isDir = isDir;
            this.displayPath = displayPath;
            this.sizeBytes = sizeBytes;
            this.parent = parent;
        }

        public String getSubtitle() {
            if (isDir) {
                int count = children != null ? children.size() : 0;
                return count + " item" + (count == 1 ? "" : "s");
            }
            if (sizeBytes < 1024) return sizeBytes + " B";
            if (sizeBytes < 1048576) return String.format(java.util.Locale.US, "%.1f KB", sizeBytes / 1024.0);
            return String.format(java.util.Locale.US, "%.1f MB", sizeBytes / 1048576.0);
        }
    }

    public interface NodeClickListener {
        void onNodeClicked(FolderNode node);
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

            boolean hasChildren = node.isDir && node.children != null && !node.children.isEmpty();
            openBtn.setVisibility(hasChildren ? View.VISIBLE : View.GONE);

            cb.setOnCheckedChangeListener(null);
            cb.setChecked(selectedPaths.contains(node.relPath));

            cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        selectRecursively(node, selectedPaths);
                    } else {
                        deselectRecursively(node, selectedPaths);
                    }
                    notifyDataSetChanged();
                }
            });

            View.OnClickListener openFolderListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (clickListener != null && node.isDir) {
                        clickListener.onNodeClicked(node);
                    }
                }
            };

            openBtn.setOnClickListener(openFolderListener);
            textLayout.setOnClickListener(openFolderListener);
            row.setOnClickListener(openFolderListener);

            return row;
        }
    }
}
