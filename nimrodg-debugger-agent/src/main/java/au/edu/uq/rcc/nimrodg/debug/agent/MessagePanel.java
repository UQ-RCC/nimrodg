package au.edu.uq.rcc.nimrodg.debug.agent;

import com.rabbitmq.client.AMQP;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.json.Json;
import javax.json.JsonStructure;
import javax.json.JsonWriter;
import javax.json.stream.JsonGenerator;
import javax.swing.AbstractListModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;

public class MessagePanel extends javax.swing.JPanel {

	private List<Message> messages;
	private _TableModel tableModel;

	public MessagePanel() {
		this.messages = null;
		initComponents();
		this.itemList.setModel(new _ListModel());
		this.itemList.addListSelectionListener(this::onListSelect);
		this.tableModel = new _TableModel();
		this.headerTable.setModel(tableModel);
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

		msg.rawJson.ifPresentOrElse(j -> {
			rawJsonPanel.setText(prettyPrint(j));
		}, () -> {
			rawJsonPanel.setText("JSON deserialise error.");
		});

		msg.msgJson.ifPresentOrElse(j -> {
			msgJsonPanel.setText(prettyPrint(j));
		}, () -> {
			msgJsonPanel.setText("JSON deserialise error.");
		});

		AMQP.BasicProperties props = msg.message.basicProperties;
		contentTypeField.setText(Optional.ofNullable(props.getContentType()).orElse(""));
		contentEncodingField.setText(Optional.ofNullable(props.getContentEncoding()).orElse(""));
		deliveryModeField.setText(Optional.ofNullable(props.getDeliveryMode()).map(i -> i.toString()).orElse(""));
		priorityField.setText(Optional.ofNullable(props.getPriority()).map(i -> i.toString()).orElse(""));
		correlationField.setText(Optional.ofNullable(props.getCorrelationId()).orElse(""));
		replyToField.setText(Optional.ofNullable(props.getReplyTo()).orElse(""));
		expirationField.setText(Optional.ofNullable(props.getExpiration()).orElse(""));
		messageIdField.setText(Optional.ofNullable(props.getMessageId()).orElse(""));
		timestampField.setText(Optional.ofNullable(props.getTimestamp()).map(t -> t.toInstant().toString()).orElse(""));
		typeField.setText(Optional.ofNullable(props.getType()).orElse(""));
		userField.setText(Optional.ofNullable(props.getUserId()).orElse(""));
		applicationField.setText(Optional.ofNullable(props.getAppId()).orElse(""));

		tableModel.setMessage(msg);
		headerTable.updateUI();
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

	public class _TableModel extends AbstractTableModel {

		private final ArrayList<Map.Entry<String, Object>> headers;

		public _TableModel() {
			headers = new ArrayList<>();
		}

		public void setMessage(Message msg) {
			headers.clear();

			if(msg == null) {
				return;
			}

			Map<String, Object> h = msg.message.basicProperties.getHeaders();
			if(h == null) {
				return;
			}


			h.forEach((k, v) -> headers.add(Map.entry(k, v)));
		}

		@Override
		public int getRowCount() {
			return headers.size();
		}

		@Override
		public int getColumnCount() {
			return 2;
		}

		@Override
		public Object getValueAt(int y, int x) {
			Map.Entry<String, Object> e = headers.get(y);
			if(x == 0)
				return e.getKey();
			else
				return e.getValue();
		}

		@Override
		public String getColumnName(int column) {
			if(column == 0)
				return "Key";
			else
				return "Value";
		}
	}


	@SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.JSplitPane jSplitPane1 = new javax.swing.JSplitPane();
        javax.swing.JScrollPane jScrollPane1 = new javax.swing.JScrollPane();
        itemList = new javax.swing.JList<>();
        javax.swing.JSplitPane jSplitPane2 = new javax.swing.JSplitPane();
        javax.swing.JTabbedPane jTabbedPane1 = new javax.swing.JTabbedPane();
        javax.swing.JScrollPane jScrollPane2 = new javax.swing.JScrollPane();
        msgJsonPanel = new javax.swing.JTextArea();
        javax.swing.JScrollPane jScrollPane4 = new javax.swing.JScrollPane();
        rawJsonPanel = new javax.swing.JTextArea();
        rawPanel = new javax.swing.JPanel();
        jPanel1 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        contentTypeField = new javax.swing.JTextField();
        javax.swing.JLabel jLabel2 = new javax.swing.JLabel();
        javax.swing.JLabel jLabel3 = new javax.swing.JLabel();
        contentEncodingField = new javax.swing.JTextField();
        deliveryModeField = new javax.swing.JTextField();
        javax.swing.JLabel jLabel5 = new javax.swing.JLabel();
        javax.swing.JLabel jLabel8 = new javax.swing.JLabel();
        priorityField = new javax.swing.JTextField();
        javax.swing.JLabel jLabel6 = new javax.swing.JLabel();
        correlationField = new javax.swing.JTextField();
        javax.swing.JLabel jLabel7 = new javax.swing.JLabel();
        replyToField = new javax.swing.JTextField();
        javax.swing.JLabel jLabel9 = new javax.swing.JLabel();
        expirationField = new javax.swing.JTextField();
        javax.swing.JLabel jLabel10 = new javax.swing.JLabel();
        messageIdField = new javax.swing.JTextField();
        timestampField = new javax.swing.JTextField();
        javax.swing.JLabel jLabel11 = new javax.swing.JLabel();
        javax.swing.JLabel jLabel12 = new javax.swing.JLabel();
        typeField = new javax.swing.JTextField();
        userField = new javax.swing.JTextField();
        javax.swing.JLabel jLabel13 = new javax.swing.JLabel();
        javax.swing.JLabel jLabel14 = new javax.swing.JLabel();
        applicationField = new javax.swing.JTextField();
        javax.swing.JLabel jLabel4 = new javax.swing.JLabel();
        javax.swing.JScrollPane jScrollPane5 = new javax.swing.JScrollPane();
        headerTable = new javax.swing.JTable();

        itemList.setModel(new javax.swing.AbstractListModel<String>() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public String getElementAt(int i) { return strings[i]; }
        });
        itemList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane1.setViewportView(itemList);

        jSplitPane1.setLeftComponent(jScrollPane1);

        jSplitPane2.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        jSplitPane2.setResizeWeight(1.0);
        jSplitPane2.setToolTipText("");

        jTabbedPane1.setPreferredSize(new java.awt.Dimension(672, 400));

        msgJsonPanel.setColumns(20);
        msgJsonPanel.setRows(5);
        jScrollPane2.setViewportView(msgJsonPanel);

        jTabbedPane1.addTab("JSON", jScrollPane2);

        rawJsonPanel.setColumns(20);
        rawJsonPanel.setRows(5);
        jScrollPane4.setViewportView(rawJsonPanel);

        jTabbedPane1.addTab("Raw JSON", jScrollPane4);

        javax.swing.GroupLayout rawPanelLayout = new javax.swing.GroupLayout(rawPanel);
        rawPanel.setLayout(rawPanelLayout);
        rawPanelLayout.setHorizontalGroup(
            rawPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 670, Short.MAX_VALUE)
        );
        rawPanelLayout.setVerticalGroup(
            rawPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 15, Short.MAX_VALUE)
        );

        jTabbedPane1.addTab("Raw", rawPanel);

        jSplitPane2.setLeftComponent(jTabbedPane1);

        jLabel1.setText("AMQP Basic Properties:");

        contentTypeField.setEditable(false);

        jLabel2.setText("Content Type");

        jLabel3.setText("Content Encoding");

        contentEncodingField.setEditable(false);

        deliveryModeField.setEditable(false);

        jLabel5.setText("Delivery Mode");

        jLabel8.setText("Priority");

        priorityField.setEditable(false);

        jLabel6.setText("Correlation");

        correlationField.setEditable(false);

        jLabel7.setText("Reply To");

        replyToField.setEditable(false);

        jLabel9.setText("Expiration");

        expirationField.setEditable(false);

        jLabel10.setText("Message ID");

        messageIdField.setEditable(false);

        timestampField.setEditable(false);

        jLabel11.setText("Type");

        jLabel12.setText("Timestamp");

        typeField.setEditable(false);

        userField.setEditable(false);

        jLabel13.setText("Application ID");

        jLabel14.setText("User ID");

        applicationField.setEditable(false);

        jLabel4.setText("Headers:");

        headerTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        headerTable.setEnabled(false);
        jScrollPane5.setViewportView(headerTable);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(jLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(jLabel5, javax.swing.GroupLayout.DEFAULT_SIZE, 103, Short.MAX_VALUE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(jPanel1Layout.createSequentialGroup()
                                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                            .addComponent(contentTypeField, javax.swing.GroupLayout.DEFAULT_SIZE, 218, Short.MAX_VALUE)
                                            .addComponent(deliveryModeField))
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                            .addComponent(jLabel8, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                            .addComponent(jLabel3, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                                    .addGroup(jPanel1Layout.createSequentialGroup()
                                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                            .addComponent(correlationField, javax.swing.GroupLayout.DEFAULT_SIZE, 218, Short.MAX_VALUE)
                                            .addComponent(expirationField))
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(jLabel7, javax.swing.GroupLayout.PREFERRED_SIZE, 122, javax.swing.GroupLayout.PREFERRED_SIZE)
                                            .addComponent(jLabel10, javax.swing.GroupLayout.PREFERRED_SIZE, 122, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                    .addGroup(jPanel1Layout.createSequentialGroup()
                                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                            .addComponent(timestampField, javax.swing.GroupLayout.DEFAULT_SIZE, 218, Short.MAX_VALUE)
                                            .addComponent(userField))
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(jLabel11, javax.swing.GroupLayout.PREFERRED_SIZE, 122, javax.swing.GroupLayout.PREFERRED_SIZE)
                                            .addComponent(jLabel13, javax.swing.GroupLayout.PREFERRED_SIZE, 122, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                            .addComponent(jLabel6, javax.swing.GroupLayout.PREFERRED_SIZE, 103, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel9, javax.swing.GroupLayout.PREFERRED_SIZE, 103, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel12, javax.swing.GroupLayout.PREFERRED_SIZE, 103, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel14, javax.swing.GroupLayout.PREFERRED_SIZE, 103, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(applicationField, javax.swing.GroupLayout.DEFAULT_SIZE, 196, Short.MAX_VALUE)
                            .addComponent(priorityField)
                            .addComponent(contentEncodingField)
                            .addComponent(replyToField)
                            .addComponent(messageIdField, javax.swing.GroupLayout.DEFAULT_SIZE, 196, Short.MAX_VALUE)
                            .addComponent(typeField, javax.swing.GroupLayout.DEFAULT_SIZE, 196, Short.MAX_VALUE)))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabel4)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jScrollPane5, javax.swing.GroupLayout.DEFAULT_SIZE, 660, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(contentTypeField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel2)
                    .addComponent(jLabel3)
                    .addComponent(contentEncodingField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(deliveryModeField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel5)
                    .addComponent(jLabel8)
                    .addComponent(priorityField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(correlationField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel6)
                    .addComponent(replyToField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel7, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(expirationField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel9)
                    .addComponent(jLabel10)
                    .addComponent(messageIdField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(timestampField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel12)
                    .addComponent(jLabel11)
                    .addComponent(typeField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(userField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel14)
                    .addComponent(jLabel13)
                    .addComponent(applicationField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel4)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane5, javax.swing.GroupLayout.PREFERRED_SIZE, 121, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        jSplitPane2.setRightComponent(jPanel1);

        jSplitPane1.setRightComponent(jSplitPane2);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 706, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 409, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField applicationField;
    private javax.swing.JTextField contentEncodingField;
    private javax.swing.JTextField contentTypeField;
    private javax.swing.JTextField correlationField;
    private javax.swing.JTextField deliveryModeField;
    private javax.swing.JTextField expirationField;
    private javax.swing.JTable headerTable;
    private javax.swing.JList<String> itemList;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JTextField messageIdField;
    private javax.swing.JTextArea msgJsonPanel;
    private javax.swing.JTextField priorityField;
    private javax.swing.JTextArea rawJsonPanel;
    private javax.swing.JPanel rawPanel;
    private javax.swing.JTextField replyToField;
    private javax.swing.JTextField timestampField;
    private javax.swing.JTextField typeField;
    private javax.swing.JTextField userField;
    // End of variables declaration//GEN-END:variables
}
