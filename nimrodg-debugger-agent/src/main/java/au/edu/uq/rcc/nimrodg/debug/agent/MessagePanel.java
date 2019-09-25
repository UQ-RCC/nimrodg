package au.edu.uq.rcc.nimrodg.debug.agent;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.json.Json;
import javax.json.JsonStructure;
import javax.json.JsonWriter;
import javax.json.stream.JsonGenerator;
import javax.swing.AbstractListModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;

public class MessagePanel extends javax.swing.JPanel {

	private List<Message> messages;

	public MessagePanel() {
		this.messages = null;
		initComponents();
		this.itemList.setModel(new _ListModel());
		this.itemList.addListSelectionListener(this::onListSelect);
	}

	public static String prettyPrint(JsonStructure json) {
		Map<String, Boolean> ops = new HashMap<>();
		ops.put(JsonGenerator.PRETTY_PRINTING, true);

		StringWriter sw = new StringWriter();

		try(JsonWriter w = Json.createWriterFactory(ops).createWriter(sw)) {
			w.write(json);
		}

		return sw.toString();
	}

	private void onListSelect(ListSelectionEvent evt) {
		if(evt.getValueIsAdjusting()) {
			return;
		}

		if(this.messages == null) {
			return;
		}

		Message msg = this.messages.get(itemList.getSelectedIndex());

		msg.json.ifPresentOrElse(j -> {
			jsonPanel.setText(prettyPrint(j));
		}, () -> {
			jsonPanel.setText("JSON deserialise error.");
		});
	}

	public void setMessages(List<Message> messages) {
		this.messages = messages;
	}

	public void refreshMessages() {
		SwingUtilities.invokeLater(() -> itemList.updateUI());
	}

	private class _ListModel extends AbstractListModel<String> {

		@Override
		public int getSize() {
			if(MessagePanel.this.messages == null) {
				return 0;
			}

			return MessagePanel.this.messages.size();
		}

		@Override
		public String getElementAt(int i) {
			return String.format("%s Message %d", MessagePanel.this.messages.get(i).incoming ? "<-" : "->", i);
		}
	}

	@SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.JSplitPane jSplitPane1 = new javax.swing.JSplitPane();
        javax.swing.JScrollPane jScrollPane1 = new javax.swing.JScrollPane();
        itemList = new javax.swing.JList<>();
        javax.swing.JTabbedPane jTabbedPane1 = new javax.swing.JTabbedPane();
        rawPanel = new javax.swing.JPanel();
        javax.swing.JScrollPane jScrollPane2 = new javax.swing.JScrollPane();
        jsonPanel = new javax.swing.JTextArea();

        itemList.setModel(new javax.swing.AbstractListModel<String>() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public String getElementAt(int i) { return strings[i]; }
        });
        itemList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane1.setViewportView(itemList);

        jSplitPane1.setLeftComponent(jScrollPane1);

        javax.swing.GroupLayout rawPanelLayout = new javax.swing.GroupLayout(rawPanel);
        rawPanel.setLayout(rawPanelLayout);
        rawPanelLayout.setHorizontalGroup(
            rawPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 486, Short.MAX_VALUE)
        );
        rawPanelLayout.setVerticalGroup(
            rawPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 399, Short.MAX_VALUE)
        );

        jTabbedPane1.addTab("Raw", rawPanel);

        jsonPanel.setColumns(20);
        jsonPanel.setRows(5);
        jScrollPane2.setViewportView(jsonPanel);

        jTabbedPane1.addTab("JSON", jScrollPane2);

        jSplitPane1.setRightComponent(jTabbedPane1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane1)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane1)
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JList<String> itemList;
    private javax.swing.JTextArea jsonPanel;
    private javax.swing.JPanel rawPanel;
    // End of variables declaration//GEN-END:variables
}
