package cn.iocoder.yudao.module.product.service.spu;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import cn.iocoder.yudao.module.product.controller.admin.property.vo.property.ProductPropertyAndValueRespVO;
import cn.iocoder.yudao.module.product.controller.admin.property.vo.ProductPropertyViewRespVO;
import cn.iocoder.yudao.module.product.controller.admin.property.vo.value.ProductPropertyValueRespVO;
import cn.iocoder.yudao.module.product.controller.admin.sku.vo.ProductSkuBaseVO;
import cn.iocoder.yudao.module.product.controller.admin.sku.vo.ProductSkuCreateOrUpdateReqVO;
import cn.iocoder.yudao.module.product.controller.admin.sku.vo.ProductSkuRespVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuCreateReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuRespVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuUpdateReqVO;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppSpuPageReqVO;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppSpuPageRespVO;
import cn.iocoder.yudao.module.product.convert.sku.ProductSkuConvert;
import cn.iocoder.yudao.module.product.convert.spu.ProductSpuConvert;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.yudao.module.product.enums.spu.ProductSpuSpecTypeEnum;
import cn.iocoder.yudao.module.product.service.brand.ProductBrandService;
import cn.iocoder.yudao.module.product.service.category.ProductCategoryService;
import cn.iocoder.yudao.module.product.service.property.ProductPropertyService;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SPU_NOT_EXISTS;

/**
 * ?????? SPU Service ?????????
 *
 * @author ????????????
 */
@Service
@Validated
public class ProductSpuServiceImpl implements ProductSpuService {

    @Resource
    private ProductSpuMapper ProductSpuMapper;

    @Resource
    private ProductCategoryService categoryService;

    @Resource
    private ProductSkuService productSkuService;

    @Resource
    private ProductPropertyService productPropertyService;

    @Resource
    private ProductBrandService brandService;

    @Override
    @Transactional
    public Long createSpu(ProductSpuCreateReqVO createReqVO) {
        // ????????????
        categoryService.validateCategoryLevel(createReqVO.getCategoryId());
        // ????????????
        brandService.validateProductBrand(createReqVO.getBrandId());
        // ??????SKU
        List<ProductSkuCreateOrUpdateReqVO> skuCreateReqList = createReqVO.getSkus();
        productSkuService.validateSkus(skuCreateReqList, createReqVO.getSpecType());
        // ?????? SPU
        ProductSpuDO spu = ProductSpuConvert.INSTANCE.convert(createReqVO);
        spu.setMarketPrice(CollectionUtils.getMaxValue(skuCreateReqList, ProductSkuCreateOrUpdateReqVO::getMarketPrice));
        spu.setMaxPrice(CollectionUtils.getMaxValue(skuCreateReqList, ProductSkuCreateOrUpdateReqVO::getPrice));
        spu.setMinPrice(CollectionUtils.getMinValue(skuCreateReqList, ProductSkuCreateOrUpdateReqVO::getPrice));
        spu.setTotalStock(CollectionUtils.getSumValue(skuCreateReqList, ProductSkuCreateOrUpdateReqVO::getStock, Integer::sum));
        ProductSpuMapper.insert(spu);
        // ?????? SKU
        productSkuService.createSkus(spu.getId(), skuCreateReqList);
        // ??????
        return spu.getId();
    }

    @Override
    @Transactional
    public void updateSpu(ProductSpuUpdateReqVO updateReqVO) {
        // ?????? SPU ????????????
        validateSpuExists(updateReqVO.getId());
        // ????????????
        categoryService.validateCategoryLevel(updateReqVO.getCategoryId());
        // ????????????
        brandService.validateProductBrand(updateReqVO.getBrandId());
        // ??????SKU
        List<ProductSkuCreateOrUpdateReqVO> skuCreateReqList = updateReqVO.getSkus();
        // ?????????????????????
        productSkuService.validateSkus(skuCreateReqList, updateReqVO.getSpecType());

        // ?????? SPU
        ProductSpuDO updateObj = ProductSpuConvert.INSTANCE.convert(updateReqVO);
        updateObj.setMarketPrice(CollectionUtils.getMaxValue(skuCreateReqList, ProductSkuCreateOrUpdateReqVO::getMarketPrice));
        updateObj.setMaxPrice(CollectionUtils.getMaxValue(skuCreateReqList, ProductSkuCreateOrUpdateReqVO::getPrice));
        updateObj.setMinPrice(CollectionUtils.getMinValue(skuCreateReqList, ProductSkuCreateOrUpdateReqVO::getPrice));
        updateObj.setTotalStock(CollectionUtils.getSumValue(skuCreateReqList, ProductSkuCreateOrUpdateReqVO::getStock, Integer::sum));
        ProductSpuMapper.updateById(updateObj);
        // ???????????? SKU
        productSkuService.updateProductSkus(updateObj.getId(), updateReqVO.getSkus());
    }

    @Override
    @Transactional
    public void deleteSpu(Long id) {
        // ????????????
        validateSpuExists(id);
        // ?????? SPU
        ProductSpuMapper.deleteById(id);
        // ??????????????? SKU
        productSkuService.deleteSkuBySpuId(id);
    }

    private void validateSpuExists(Long id) {
        if (ProductSpuMapper.selectById(id) == null) {
            throw exception(SPU_NOT_EXISTS);
        }
    }

    @Override
    // TODO @?????????????????? review ???
    public ProductSpuRespVO getSpu(Long id) {
        ProductSpuDO spu = ProductSpuMapper.selectById(id);
        ProductSpuRespVO spuVO = ProductSpuConvert.INSTANCE.convert(spu);
        if (null != spuVO) {
            List<ProductSkuRespVO> skuReqs = ProductSkuConvert.INSTANCE.convertList(productSkuService.getSkusBySpuId(id));
            spuVO.setSkus(skuReqs);
            List<ProductSkuRespVO.Property> properties = new ArrayList<>();
            // ?????? sku ????????????
            if(spu.getSpecType().equals(ProductSpuSpecTypeEnum.DISABLE.getType())) {
                for (ProductSkuRespVO productSkuRespVO : skuReqs) {
                    properties.addAll(productSkuRespVO.getProperties());
                }
                Map<Long, List<ProductSkuBaseVO.Property>> propertyMaps = properties.stream().collect(Collectors.groupingBy(ProductSkuBaseVO.Property::getPropertyId));
                List<ProductPropertyAndValueRespVO> propertyAndValueList = productPropertyService.getPropertyAndValueList(new ArrayList<>(propertyMaps.keySet()));
                // ???????????????????????????
                List<ProductPropertyViewRespVO> productPropertyViews = new ArrayList<>();
                propertyAndValueList.forEach(p -> {
                    ProductPropertyViewRespVO productPropertyViewRespVO = new ProductPropertyViewRespVO();
                    productPropertyViewRespVO.setPropertyId(p.getId());
                    productPropertyViewRespVO.setName(p.getName());
                    List<ProductPropertyViewRespVO.Tuple2> propertyValues = new ArrayList<>();
                    Map<Long, ProductPropertyValueRespVO> propertyValueMaps = p.getValues().stream().collect(Collectors.toMap(ProductPropertyValueRespVO::getId, pv -> pv));
                    propertyMaps.get(p.getId()).forEach(pv -> {
                        ProductPropertyViewRespVO.Tuple2 tuple2 = new ProductPropertyViewRespVO.Tuple2(pv.getValueId(), propertyValueMaps.get(pv.getValueId()).getName());
                        propertyValues.add(tuple2);
                    });
                    productPropertyViewRespVO.setPropertyValues(propertyValues.stream().distinct().collect(Collectors.toList()));
                    productPropertyViews.add(productPropertyViewRespVO);
                });
                spuVO.setProductPropertyViews(productPropertyViews);
            }
            // ????????????
            if (null != spuVO.getCategoryId()) {
                LinkedList<Long> categoryArray = new LinkedList<>();
                Long parentId = spuVO.getCategoryId();
                categoryArray.addFirst(parentId);
                while (parentId != 0) {
                    parentId = categoryService.getCategory(parentId).getParentId();
                    if (parentId > 0) {
                        categoryArray.addFirst(parentId);
                    }
                }
                spuVO.setCategoryIds(categoryArray);
            }
        }
        return spuVO;
    }

    @Override
    public List<ProductSpuDO> getSpuList(Collection<Long> ids) {
        return ProductSpuMapper.selectBatchIds(ids);
    }

    @Override
    public PageResult<ProductSpuRespVO> getSpuPage(ProductSpuPageReqVO pageReqVO) {
        PageResult<ProductSpuRespVO> spuVOs = ProductSpuConvert.INSTANCE.convertPage(ProductSpuMapper.selectPage(pageReqVO));
        // ?????? sku ?????????
        List<Long> spuIds = spuVOs.getList().stream().map(ProductSpuRespVO::getId).collect(Collectors.toList());
        List<ProductSkuRespVO> skus = ProductSkuConvert.INSTANCE.convertList(productSkuService.getSkusBySpuIds(spuIds));
        // TODO @franky????????? CollUtil ?????????????????????
        // TODO ?????????????????????
//        Map<Long, List<ProductSkuRespVO>> skuMap = skus.stream().collect(Collectors.groupingBy(ProductSkuRespVO::getSpuId));
//        // ??? spu ??? sku ????????????
//        spuVOs.getList().forEach(p -> p.setSkus(skuMap.get(p.getId())));
        return spuVOs;
    }

    @Override
    public PageResult<AppSpuPageRespVO> getSpuPage(AppSpuPageReqVO pageReqVO) {
        PageResult<ProductSpuDO> productSpuDOPageResult = ProductSpuMapper.selectPage(ProductSpuConvert.INSTANCE.convert(pageReqVO));
        PageResult<AppSpuPageRespVO> pageResult = new PageResult<>();
        List<AppSpuPageRespVO> collect = productSpuDOPageResult.getList()
                .stream()
                .map(ProductSpuConvert.INSTANCE::convertAppResp)
                .collect(Collectors.toList());
        pageResult.setList(collect);
        pageResult.setTotal(productSpuDOPageResult.getTotal());
        return pageResult;
    }

}
