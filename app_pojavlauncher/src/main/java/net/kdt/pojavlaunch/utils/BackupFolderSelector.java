package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
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

        final Set<String> selectedItems = new HashSet<String>(BackupManager.getSelectedItems(activity));
        final Stack<FolderNode> navStack = new Stack<FolderNode>();

        // Build root nodes
        final FolderNode rootNode = buildRootNode(activity);
        navStack.push(rootNode);

        // Layout container
        final LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 16);

        // Header layout
        final LinearLayout headerLayout = new LinearLayout(activity);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);
        headerLayout.setPadding(0, 0, 0, 16);

        final Button backBtn = new Button(activity);
        backBtn.setText("◀ Back");
        backBtn.setEnabled(false);

        final TextView pathView = new TextView(activity);
        pathView.setText("Root");
        pathView.setTextSize(16);
        pathView.setTypeface(null, android.graphics.Typeface.BOLD);
        pathView.setPadding(16, 0, 0, 0);

        headerLayout.addView(backBtn);
        headerLayout.addView(pathView);

        // Quick action buttons
        final LinearLayout quickLayout = new LinearLayout(activity);
        quickLayout.setOrientation(LinearLayout.HORIZONTAL);
        quickLayout.setPadding(0, 0, 0, 12);

        final Button selectAllBtn = new Button(activity);
        selectAllBtn.setText("Select All");
        final Button deselectAllBtn = new Button(activity);
        deselectAllBtn.setText("Deselect All");

        quickLayout.addView(selectAllBtn);
        quickLayout.addView(deselectAllBtn);

        // List view
        final ListView listView = new ListView(activity);
        listView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 800));

        layout.addView(headerLayout);
        layout.addView(quickLayout);
        layout.addView(listView);

        final SelectorAdapter adapter = new SelectorAdapter(activity, navStack.peek().children, selectedItems);
        listView.setAdapter(adapter);

        final Runnable refreshUI = new Runnable() {
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
                    refreshUI.run();
                }
            }
        });

        selectAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FolderNode current = navStack.peek();
                for (FolderNode child : current.children) {
                    selectedItems.add(child.key);
                }
                adapter.notifyDataSetChanged();
            }
        });

        deselectAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FolderNode current = navStack.peek();
                for (FolderNode child : current.children) {
                    selectedItems.remove(child.key);
                }
                adapter.notifyDataSetChanged();
            }
        });

        adapter.setOnNodeClickListener(new NodeClickListener() {
            @Override
            public void onNodeClicked(FolderNode node) {
                if (node.isFolder && node.children != null && !node.children.isEmpty()) {
                    navStack.push(node);
                    refreshUI.run();
                }
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Select Backup Folders");
        builder.setView(layout);
        builder.setPositiveButton("Save Selection", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                BackupManager.saveSelectedItems(activity, selectedItems);
                Toast.makeText(activity, "Backup selections saved (" + selectedItems.size() + " items active)", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private static FolderNode buildRootNode(Context context) {
        FolderNode root = new FolderNode("Root", "", true, "Root");
        root.children = new ArrayList<FolderNode>();

        File filesDir = context.getExternalFilesDir(null);
        File mcDir = new File(filesDir, ".minecraft");
        File controlDir = new File(filesDir, "controlmap");
        File accountsDir = new File(context.getFilesDir(), "accounts");

        // 1. Accounts
        FolderNode accountsNode = new FolderNode("User Accounts", "ACCOUNTS", true, "Accounts");
        accountsNode.children = new ArrayList<FolderNode>();
        if (accountsDir.exists() && accountsDir.listFiles() != null) {
            for (File acc : accountsDir.listFiles()) {
                if (acc.isFile() && acc.getName().endsWith(".json")) {
                    accountsNode.children.add(new FolderNode("Account: " + acc.getName().replace(".json", ""), "ACCOUNTS/" + acc.getName(), false, "Accounts/" + acc.getName()));
                }
            }
        }
        root.children.add(accountsNode);

        // 2. Control maps
        FolderNode controlsNode = new FolderNode("Custom Controls", "controlmap", true, "Control Maps");
        controlsNode.children = new ArrayList<FolderNode>();
        if (controlDir.exists() && controlDir.listFiles() != null) {
            for (File c : controlDir.listFiles()) {
                if (c.isFile()) {
                    controlsNode.children.add(new FolderNode("Layout: " + c.getName(), "controlmap/" + c.getName(), false, "Controls/" + c.getName()));
                }
            }
        }
        root.children.add(controlsNode);

        // 3. Minecraft Folders & Files
        if (mcDir.exists() && mcDir.listFiles() != null) {
            File[] files = mcDir.listFiles();
            for (File f : files) {
                String name = f.getName();
                if (name.equals("assets") || name.equals("libraries") || name.equals(".fabric")) {
                    continue; // Skip static game runtime binaries
                }

                if (f.isDirectory()) {
                    FolderNode dirNode = new FolderNode(name + "/", name, true, name);
                    dirNode.children = new ArrayList<FolderNode>();
                    File[] subFiles = f.listFiles();
                    if (subFiles != null) {
                        for (File sf : subFiles) {
                            String displayName = sf.isDirectory() ? (sf.getName() + "/") : sf.getName();
                            dirNode.children.add(new FolderNode(displayName, name + "/" + sf.getName(), sf.isDirectory(), name + "/" + sf.getName()));
                        }
                    }
                    root.children.add(dirNode);
                } else {
                    root.children.add(new FolderNode(name, name, false, name));
                }
            }
        }

        return root;
    }

    public static class FolderNode {
        public String title;
        public String key;
        public boolean isFolder;
        public String displayPath;
        public List<FolderNode> children;

        public FolderNode(String title, String key, boolean isFolder, String displayPath) {
            this.title = title;
            this.key = key;
            this.isFolder = isFolder;
            this.displayPath = displayPath;
        }
    }

    public interface NodeClickListener {
        void onNodeClicked(FolderNode node);
    }

    private static class SelectorAdapter extends BaseAdapter {
        private final Context context;
        private List<FolderNode> items;
        private final Set<String> selectedItems;
        private NodeClickListener clickListener;

        public SelectorAdapter(Context context, List<FolderNode> items, Set<String> selectedItems) {
            this.context = context;
            this.items = items != null ? items : new ArrayList<FolderNode>();
            this.selectedItems = selectedItems;
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
                row.setPadding(12, 16, 12, 16);

                CheckBox cb = new CheckBox(context);
                cb.setId(1001);

                TextView tv = new TextView(context);
                tv.setId(1002);
                tv.setTextSize(15);
                tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

                TextView arrow = new TextView(context);
                arrow.setId(1003);
                arrow.setText("▶");
                arrow.setTextSize(16);
                arrow.setPadding(16, 0, 16, 0);

                row.addView(cb);
                row.addView(tv);
                row.addView(arrow);
            } else {
                row = (LinearLayout) convertView;
            }

            final FolderNode node = getItem(position);
            CheckBox cb = row.findViewById(1001);
            TextView tv = row.findViewById(1002);
            TextView arrow = row.findViewById(1003);

            tv.setText((node.isFolder ? "📁 " : "📄 ") + node.title);
            arrow.setVisibility(node.isFolder && node.children != null && !node.children.isEmpty() ? View.VISIBLE : View.GONE);

            cb.setOnCheckedChangeListener(null);
            cb.setChecked(selectedItems.contains(node.key));
            cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        selectedItems.add(node.key);
                    } else {
                        selectedItems.remove(node.key);
                    }
                }
            });

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (clickListener != null && node.isFolder) {
                        clickListener.onNodeClicked(node);
                    }
                }
            });

            return row;
        }
    }
}
