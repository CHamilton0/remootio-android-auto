package com.example.remootioandroidws;

import static androidx.car.app.model.Action.BACK;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.Screen;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.SectionedItemList;
import androidx.car.app.model.Template;

public class RemootioScreen extends Screen {
    public RemootioScreen(CarContext carContext) {
        super(carContext);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        ListTemplate.Builder templateBuilder = new ListTemplate.Builder();

        ItemList radioList =
                new ItemList.Builder()
                        .addItem( new Row.Builder()
                                .setTitle("Test!")
                                .addText("click on any option to see toast notification")
                                .build())
                        .addItem(new Row.Builder().setTitle("Option 2").build())
                        .addItem(new Row.Builder().setTitle("Option 3").build())
                        .setOnSelectedListener(this::onSelected)
                        .build();
        templateBuilder.addSectionedList(
                SectionedItemList.create(radioList, "Sample list"));

        return templateBuilder.setTitle("List Selection Demo").setHeaderAction(BACK).build();
    }

    private void onSelected(int index) {
        CarToast.makeText(getCarContext(), "Changed selection to index: " + index, CarToast.LENGTH_LONG)
                .show();

    }
}